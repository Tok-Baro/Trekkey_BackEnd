package com.api.trekkey.domain.submission.admin.service;

import com.api.trekkey.domain.contest.entity.Contest;
import com.api.trekkey.domain.contest.exception.ContestErrorResponseCode;
import com.api.trekkey.domain.contest.repository.ContestRepository;
import com.api.trekkey.domain.submission.entity.Submission;
import com.api.trekkey.domain.submission.entity.SubmissionFile;
import com.api.trekkey.domain.submission.exception.SubmissionErrorResponseCode;
import com.api.trekkey.domain.submission.publicapi.web.dto.SubmissionFileRes;
import com.api.trekkey.domain.submission.publicapi.web.dto.SubmissionRes;
import com.api.trekkey.domain.submission.repository.SubmissionFileRepository;
import com.api.trekkey.domain.submission.repository.SubmissionRepository;
import com.api.trekkey.domain.submission.support.FileDownload;
import com.api.trekkey.domain.submission.support.FileStoragePort;
import com.api.trekkey.domain.user.entity.User;
import com.api.trekkey.domain.user.exception.UserErrorResponseCode;
import com.api.trekkey.domain.user.repository.UserRepository;
import com.api.trekkey.global.exception.CustomException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubmissionAdminServiceImpl implements SubmissionAdminService {

    private final UserRepository userRepository;
    private final ContestRepository contestRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionFileRepository submissionFileRepository;
    private final FileStoragePort fileStoragePort;

    @Override
    public List<SubmissionRes> getSubmissions(Long adminUserId, String contestPublicId) {
        User admin = findAdmin(adminUserId);

        Contest contest = contestRepository.findByPublicId(contestPublicId)
                .orElseThrow(() -> new CustomException(ContestErrorResponseCode.CONTEST_NOT_FOUND));
        if (!contest.getOrganization().getId().equals(admin.getOrganization().getId())) {
            throw new CustomException(ContestErrorResponseCode.CONTEST_FORBIDDEN);
        }

        List<Submission> submissions = submissionRepository.findAllByContestId(contest.getId());

        // 파일 일괄 조회 후 제출물별 그룹핑 (N+1 방지)
        List<Long> submissionIds = submissions.stream().map(Submission::getId).toList();
        Map<Long, List<SubmissionFileRes>> filesBySubmissionId = submissionIds.isEmpty()
                ? Map.of()
                : submissionFileRepository.findAllBySubmissionIdIn(submissionIds).stream()
                        .collect(Collectors.groupingBy(
                                file -> file.getSubmission().getId(),
                                Collectors.mapping(SubmissionFileRes::from, Collectors.toList())));

        return submissions.stream()
                .map(submission -> SubmissionRes.from(
                        submission,
                        filesBySubmissionId.getOrDefault(submission.getId(), List.of())))
                .toList();
    }

    @Override
    public FileDownload downloadFile(Long adminUserId, Long fileId) {
        User admin = findAdmin(adminUserId);

        SubmissionFile file = submissionFileRepository.findById(fileId)
                .orElseThrow(() -> new CustomException(SubmissionErrorResponseCode.SUBMISSION_FILE_NOT_FOUND));

        //타 조직 제출물은 존재 여부를 노출하지 않고 404로 응답한다
        Long fileOrganizationId =
                file.getSubmission().getTeam().getContest().getOrganization().getId();
        if (!fileOrganizationId.equals(admin.getOrganization().getId())) {
            throw new CustomException(SubmissionErrorResponseCode.SUBMISSION_FILE_NOT_FOUND);
        }

        return new FileDownload(
                file.getOriginalName(),
                file.getContentType(),
                file.getSizeBytes(),
                fileStoragePort.open(file.getStorageKey()));
    }

    //======= 헬퍼 메서드 ==========

    private User findAdmin(Long adminUserId) {
        return userRepository.findById(adminUserId)
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND));
    }
}
