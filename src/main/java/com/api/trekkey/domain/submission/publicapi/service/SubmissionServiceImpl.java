package com.api.trekkey.domain.submission.publicapi.service;

import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.StageStatus;
import com.api.trekkey.domain.contest.entity.StageType;
import com.api.trekkey.domain.contest.repository.ContestStageRepository;
import com.api.trekkey.domain.submission.entity.Submission;
import com.api.trekkey.domain.submission.entity.SubmissionFile;
import com.api.trekkey.domain.submission.entity.SubmissionStatus;
import com.api.trekkey.domain.submission.exception.SubmissionErrorResponseCode;
import com.api.trekkey.domain.submission.publicapi.web.dto.SubmissionFileRes;
import com.api.trekkey.domain.submission.publicapi.web.dto.SubmissionRes;
import com.api.trekkey.domain.submission.repository.SubmissionFileRepository;
import com.api.trekkey.domain.submission.repository.SubmissionRepository;
import com.api.trekkey.domain.submission.support.FileDownload;
import com.api.trekkey.domain.submission.support.FileStoragePort;
import com.api.trekkey.domain.submission.support.StoredFile;
import com.api.trekkey.domain.team.entity.Team;
import com.api.trekkey.domain.team.entity.TeamStatus;
import com.api.trekkey.domain.team.exception.TeamErrorResponseCode;
import com.api.trekkey.domain.team.repository.TeamRepository;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubmissionServiceImpl implements SubmissionService {

    // 프론트 제출 폼과 동일한 허용 확장자 목록
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "ppt", "pptx", "doc", "docx", "hwp", "hwpx",
            "xls", "xlsx", "txt", "zip", "mp4", "mov", "avi", "png", "jpg", "jpeg");

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final ContestStageRepository contestStageRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionFileRepository submissionFileRepository;
    private final FileStoragePort fileStoragePort;

    @Override
    @Transactional
    public SubmissionRes submit(Long userId, String teamPublicId, String title, List<MultipartFile> files) {
        User user = findUser(userId);
        Team team = findLeaderTeamForUpdate(teamPublicId, user.getId());

        //검토중·승인 상태의 팀만 제출할 수 있다 (보완요청·반려 팀은 불가)
        if (team.getStatus() != TeamStatus.PENDING && team.getStatus() != TeamStatus.APPROVED) {
            throw new CustomException(SubmissionErrorResponseCode.SUBMISSION_NOT_OPEN);
        }
        validateSubmissionStageOpen(team);
        validateFiles(files);

        LocalDateTime now = LocalDateTime.now();
        // 동시 덮어쓰기를 직렬화해 DB 파일 목록과 저장소 객체 교체 순서를 보호한다.
        Submission submission = submissionRepository.findByTeamIdForUpdate(team.getId()).orElse(null);
        List<String> previousStorageKeys = new ArrayList<>();

        if (submission == null) {
            submission = submissionRepository.save(Submission.builder()
                    .team(team)
                    .title(title.trim())
                    .status(SubmissionStatus.SUBMITTED)
                    .submittedAt(now)
                    .build());
        } else {
            // 심사 시작으로 이미 확정된 제출물은 덮어쓸 수 없다.
            if (submission.isFinalized()) {
                throw new CustomException(SubmissionErrorResponseCode.SUBMISSION_FINALIZED);
            }
            // 기존 DB 행은 현재 트랜잭션에서 교체하고 객체는 커밋 후 삭제한다.
            //현재읽기(FOR UPDATE)로 조회해야 잠금 대기 중 커밋된 직전 파일도 보인다 (RR 스냅샷 누락 방지)
            submissionFileRepository.findAllBySubmissionIdForUpdate(submission.getId())
                    .forEach(file -> previousStorageKeys.add(file.getStorageKey()));
            submissionFileRepository.deleteAllBySubmissionIdBulk(submission.getId());
            submission.overwrite(title.trim(), now);
        }

        List<String> newStorageKeys = new ArrayList<>();
        List<SubmissionFile> savedFiles;
        try {
            // 새 객체를 저장하며 스트림에서 SHA-256을 함께 계산한다. 별도 해시 워커는 사용하지 않는다.
            savedFiles = storeFiles(submission, user, files, newStorageKeys);
            submissionFileRepository.saveAll(savedFiles);
            registerFileCleanup(previousStorageKeys, newStorageKeys);
        } catch (RuntimeException exception) {
            newStorageKeys.forEach(fileStoragePort::delete);
            throw exception;
        }

        return SubmissionRes.from(submission, toFileResList(savedFiles));
    }

    @Override
    public SubmissionRes getMySubmission(Long userId, String teamPublicId) {
        User user = findUser(userId);
        Team team = findLeaderTeam(teamPublicId, user.getId());

        Submission submission = submissionRepository.findByTeamId(team.getId())
                .orElseThrow(() -> new CustomException(SubmissionErrorResponseCode.SUBMISSION_NOT_FOUND));

        List<SubmissionFileRes> files = submissionFileRepository.findAllBySubmissionId(submission.getId())
                .stream().map(SubmissionFileRes::from).toList();

        return SubmissionRes.from(submission, files);
    }

    @Override
    public FileDownload downloadFile(Long userId, Long fileId) {
        User user = findUser(userId);

        SubmissionFile file = submissionFileRepository.findById(fileId)
                .orElseThrow(() -> new CustomException(SubmissionErrorResponseCode.SUBMISSION_FILE_NOT_FOUND));

        //팀 대표 본인 파일만 — 소유권은 DB 기준으로 재검증한다
        if (!file.getSubmission().getTeam().getLeaderUser().getId().equals(user.getId())) {
            throw new CustomException(SubmissionErrorResponseCode.SUBMISSION_FORBIDDEN);
        }

        return new FileDownload(
                file.getOriginalName(),
                file.getContentType(),
                file.getSizeBytes(),
                fileStoragePort.open(file.getStorageKey()));
    }

    //======= 헬퍼 메서드 ==========

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND));
    }

    private Team findLeaderTeam(String teamPublicId, Long userId) {
        Team team = teamRepository.findByPublicId(teamPublicId)
                .orElseThrow(() -> new CustomException(TeamErrorResponseCode.TEAM_NOT_FOUND));
        if (!team.getLeaderUser().getId().equals(userId)) {
            throw new CustomException(SubmissionErrorResponseCode.SUBMISSION_FORBIDDEN);
        }
        return team;
    }

    private Team findLeaderTeamForUpdate(String teamPublicId, Long userId) {
        Team team = teamRepository.findByPublicIdForUpdate(teamPublicId)
                .orElseThrow(() -> new CustomException(TeamErrorResponseCode.TEAM_NOT_FOUND));
        if (!team.getLeaderUser().getId().equals(userId)) {
            throw new CustomException(SubmissionErrorResponseCode.SUBMISSION_FORBIDDEN);
        }
        return team;
    }

    // 제출 단계(SUBMISSION)가 OPEN이고 마감 시각이 지나지 않았는지 검증한다
    private void validateSubmissionStageOpen(Team team) {
        LocalDateTime now = LocalDateTime.now();
        ContestStage submissionStage = contestStageRepository
                .findAllByContestIdOrderBySequenceNoAsc(team.getContest().getId()).stream()
                .filter(stage -> stage.getStageType() == StageType.SUBMISSION)
                .findFirst()
                .orElse(null);

        boolean open = submissionStage != null
                && submissionStage.getStatus() == StageStatus.OPEN
                && (submissionStage.getEndsAt() == null || !now.isAfter(submissionStage.getEndsAt()));
        if (!open) {
            throw new CustomException(SubmissionErrorResponseCode.SUBMISSION_NOT_OPEN);
        }
    }

    private void validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty() || files.stream().allMatch(MultipartFile::isEmpty)) {
            throw new CustomException(SubmissionErrorResponseCode.SUBMISSION_FILE_REQUIRED);
        }
        for (MultipartFile file : files) {
            String name = file.getOriginalFilename();
            int dotIndex = name == null ? -1 : name.lastIndexOf('.');
            String extension = dotIndex < 0 ? "" : name.substring(dotIndex + 1).toLowerCase();
            if (!ALLOWED_EXTENSIONS.contains(extension)) {
                throw new CustomException(SubmissionErrorResponseCode.SUBMISSION_FILE_TYPE_INVALID);
            }
        }
    }

    private List<SubmissionFile> storeFiles(
            Submission submission,
            User uploader,
            List<MultipartFile> files,
            List<String> newStorageKeys) {
        List<SubmissionFile> result = new ArrayList<>();
        String keyPrefix = "submissions/" + submission.getPublicId();
        for (MultipartFile file : files) {
            try (InputStream inputStream = file.getInputStream()) {
                StoredFile stored = fileStoragePort.store(keyPrefix, file.getOriginalFilename(), inputStream);
                newStorageKeys.add(stored.storageKey());
                result.add(SubmissionFile.builder()
                        .submission(submission)
                        .uploadedBy(uploader)
                        .originalName(file.getOriginalFilename())
                        .contentType(file.getContentType() == null
                                ? "application/octet-stream" : file.getContentType())
                        .sizeBytes(stored.sizeBytes())
                        .storageKey(stored.storageKey())
                        .sha256(stored.sha256())
                        .build());
            } catch (IOException e) {
                throw new CustomException(SubmissionErrorResponseCode.SUBMISSION_STORAGE_ERROR);
            }
        }
        return result;
    }

    private void registerFileCleanup(List<String> previousStorageKeys, List<String> newStorageKeys) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            previousStorageKeys.forEach(fileStoragePort::delete);
            return;
        }

        List<String> oldFiles = List.copyOf(previousStorageKeys);
        List<String> newFiles = List.copyOf(newStorageKeys);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                oldFiles.forEach(fileStoragePort::delete);
            }

            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    newFiles.forEach(fileStoragePort::delete);
                }
            }
        });
    }

    private List<SubmissionFileRes> toFileResList(List<SubmissionFile> files) {
        return files.stream().map(SubmissionFileRes::from).toList();
    }
}
