package com.api.trekkey.domain.review.web.dto.response;

import com.api.trekkey.domain.review.entity.ReviewAssignment;
import com.api.trekkey.domain.review.entity.ReviewAssignmentStatus;
import java.time.LocalDateTime;

public record ReviewAssignmentRes(
        Long id,
        Long judgeId,
        String judgeName,
        Long reviewStageId,
        Long reviewRoundEntryId,
        String submissionPublicId,
        String submissionTitle,
        ReviewAssignmentStatus status,
        LocalDateTime assignedAt,
        LocalDateTime dueAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ReviewAssignmentRes from(
            ReviewAssignment assignment
    ) {
        return new ReviewAssignmentRes(
                assignment.getId(),
                assignment.getContestJudge().getId(),
                assignment.getContestJudge().getName(),
                assignment.getReviewRoundEntry()
                        .getReviewStage()
                        .getId(),
                assignment.getReviewRoundEntry().getId(),
                assignment.getReviewRoundEntry()
                        .getSubmission()
                        .getPublicId(),
                assignment.getReviewRoundEntry()
                        .getSubmission()
                        .getTitle(),
                assignment.getStatus(),
                assignment.getAssignedAt(),
                assignment.getDueAt(),
                assignment.getCompletedAt(),
                assignment.getCreatedAt(),
                assignment.getUpdatedAt()
        );
    }
}
