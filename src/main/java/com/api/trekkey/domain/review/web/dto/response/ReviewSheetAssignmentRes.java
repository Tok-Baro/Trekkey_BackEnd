package com.api.trekkey.domain.review.web.dto.response;

import com.api.trekkey.domain.review.entity.ReviewAssignment;
import com.api.trekkey.domain.review.entity.ReviewAssignmentStatus;
import java.time.LocalDateTime;

public record ReviewSheetAssignmentRes(
        Long assignmentId,
        String submissionPublicId,
        String submissionTitle,
        ReviewAssignmentStatus status,
        LocalDateTime dueAt,
        LocalDateTime completedAt
) {
    public static ReviewSheetAssignmentRes from(
            ReviewAssignment assignment
    ) {
        return new ReviewSheetAssignmentRes(
                assignment.getId(),
                assignment.getReviewRoundEntry()
                        .getSubmission()
                        .getPublicId(),
                assignment.getReviewRoundEntry()
                        .getSubmission()
                        .getTitle(),
                assignment.getStatus(),
                assignment.getDueAt(),
                assignment.getCompletedAt()
        );
    }
}
