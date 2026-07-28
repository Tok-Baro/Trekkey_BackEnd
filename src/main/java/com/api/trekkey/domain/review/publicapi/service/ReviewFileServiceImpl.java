package com.api.trekkey.domain.review.publicapi.service;

import com.api.trekkey.domain.review.entity.ContestJudge;
import com.api.trekkey.domain.review.exception.ReviewErrorResponseCode;
import com.api.trekkey.domain.review.repository.ReviewAssignmentRepository;
import com.api.trekkey.domain.review.support.ReviewLinkAuthenticator;
import com.api.trekkey.domain.review.publicapi.web.dto.request.ReviewAccessReq;
import com.api.trekkey.domain.submission.entity.SubmissionFile;
import com.api.trekkey.domain.submission.repository.SubmissionFileRepository;
import com.api.trekkey.domain.submission.support.FileDownload;
import com.api.trekkey.domain.submission.support.FileStoragePort;
import com.api.trekkey.global.exception.CustomException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ReviewFileServiceImpl implements ReviewFileService {

    private final ReviewLinkAuthenticator reviewLinkAuthenticator;
    private final ReviewAssignmentRepository reviewAssignmentRepository;
    private final SubmissionFileRepository submissionFileRepository;
    private final FileStoragePort fileStoragePort;
    private final Clock clock;

    @Override
    public void validateFileAccess(
            Long fileId,
            ReviewAccessReq req
    ) {
        findAccessibleFile(fileId, req);
    }

    @Override
    public FileDownload downloadFile(
            Long fileId,
            ReviewAccessReq req
    ) {
        SubmissionFile file = findAccessibleFile(fileId, req);
        return new FileDownload(
                file.getOriginalName(),
                file.getContentType(),
                file.getSizeBytes(),
                fileStoragePort.open(file.getStorageKey()));
    }

    private SubmissionFile findAccessibleFile(
            Long fileId,
            ReviewAccessReq req
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        String rawToken = req == null ? null : req.token();
        ContestJudge judge = reviewLinkAuthenticator.authenticate(
                rawToken,
                now);
        SubmissionFile file = submissionFileRepository.findById(fileId)
                .orElseThrow(this::assignmentNotFound);

        boolean assigned = reviewAssignmentRepository
                .findAllWithRoundByJudgeIdAndSubmissionId(
                        judge.getId(),
                        file.getSubmission().getId())
                .stream()
                .anyMatch(assignment ->
                        assignment.isVisibleToJudgeAt(now));
        if (!assigned) {
            throw assignmentNotFound();
        }
        return file;
    }

    private CustomException assignmentNotFound() {
        return new CustomException(
                ReviewErrorResponseCode.REVIEW_ASSIGNMENT_NOT_FOUND);
    }
}
