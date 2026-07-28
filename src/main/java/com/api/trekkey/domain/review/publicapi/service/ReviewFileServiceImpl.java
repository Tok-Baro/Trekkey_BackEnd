package com.api.trekkey.domain.review.publicapi.service;

import com.api.trekkey.domain.review.entity.ContestJudge;
import com.api.trekkey.domain.review.entity.ReviewAssignmentStatus;
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
@Transactional(readOnly = true)
public class ReviewFileServiceImpl implements ReviewFileService {

    private final ReviewLinkAuthenticator reviewLinkAuthenticator;
    private final ReviewAssignmentRepository reviewAssignmentRepository;
    private final SubmissionFileRepository submissionFileRepository;
    private final FileStoragePort fileStoragePort;
    private final Clock clock;

    @Override
    public FileDownload downloadFile(
            Long fileId,
            ReviewAccessReq req
    ) {
        String rawToken = req == null ? null : req.token();
        ContestJudge judge = reviewLinkAuthenticator.authenticate(
                rawToken,
                LocalDateTime.now(clock));
        SubmissionFile file = submissionFileRepository.findById(fileId)
                .orElseThrow(this::assignmentNotFound);

        boolean assigned = reviewAssignmentRepository
                .existsByContestJudgeIdAndReviewRoundEntrySubmissionIdAndStatusNot(
                        judge.getId(),
                        file.getSubmission().getId(),
                        ReviewAssignmentStatus.CANCELED);
        if (!assigned) {
            throw assignmentNotFound();
        }

        return new FileDownload(
                file.getOriginalName(),
                file.getContentType(),
                file.getSizeBytes(),
                fileStoragePort.open(file.getStorageKey()));
    }

    private CustomException assignmentNotFound() {
        return new CustomException(
                ReviewErrorResponseCode.REVIEW_ASSIGNMENT_NOT_FOUND);
    }
}
