package com.api.trekkey.domain.submission.publicapi.service;

import com.api.trekkey.domain.contest.entity.ContestStage;
import com.api.trekkey.domain.contest.entity.StageStatus;
import com.api.trekkey.domain.contest.entity.StageType;
import com.api.trekkey.domain.contest.repository.ContestStageRepository;
import com.api.trekkey.domain.submission.entity.IntegrityStatus;
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
        Team team = findLeaderTeam(teamPublicId, user.getId());

        //검토중·승인 상태의 팀만 제출할 수 있다 (보완요청·반려 팀은 불가)
        if (team.getStatus() != TeamStatus.PENDING && team.getStatus() != TeamStatus.APPROVED) {
            throw new CustomException(SubmissionErrorResponseCode.SUBMISSION_NOT_OPEN);
        }
        validateSubmissionStageOpen(team);
        validateFiles(files);

        LocalDateTime now = LocalDateTime.now();
        Submission submission = submissionRepository.findByTeamId(team.getId()).orElse(null);
        List<String> previousStorageKeys = new ArrayList<>();

        if (submission == null) {
            submission = submissionRepository.save(Submission.builder()
                    .team(team)
                    .title(title.trim())
                    .status(SubmissionStatus.SUBMITTED)
                    .integrityStatus(IntegrityStatus.STALE)
                    .submittedAt(now)
                    .build());
        } else {
            //제출 잠금 이후에는 덮어쓰기를 거부한다 (erd-mvp §5)
            if (submission.isFinalized()) {
                throw new CustomException(SubmissionErrorResponseCode.SUBMISSION_FINALIZED);
            }
            //기존 파일은 DB에서 제거하고, 객체는 commit 이후 정리 대상으로 모아둔다
            submissionFileRepository.findAllBySubmissionId(submission.getId())
                    .forEach(file -> previousStorageKeys.add(file.getStorageKey()));
            submissionFileRepository.deleteAllBySubmissionId(submission.getId());
            submission.overwrite(title.trim(), now);
        }

        //새 객체를 저장하며 스트림에서 SHA-256을 함께 계산한다 — 별도 해시 워커 불필요
        List<SubmissionFile> savedFiles = storeFiles(submission, user, files);
        submissionFileRepository.saveAll(savedFiles);
        submission.markIntegrityReady();

        //이전 객체 정리 — 실패해도 본 트랜잭션을 깨지 않는다 (best effort)
        previousStorageKeys.forEach(fileStoragePort::delete);

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

    private List<SubmissionFile> storeFiles(Submission submission, User uploader, List<MultipartFile> files) {
        List<SubmissionFile> result = new ArrayList<>();
        String keyPrefix = "submissions/" + submission.getPublicId();
        for (MultipartFile file : files) {
            try (InputStream inputStream = file.getInputStream()) {
                StoredFile stored = fileStoragePort.store(keyPrefix, file.getOriginalFilename(), inputStream);
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

    private List<SubmissionFileRes> toFileResList(List<SubmissionFile> files) {
        return files.stream().map(SubmissionFileRes::from).toList();
    }
}
