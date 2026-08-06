package com.api.trekkey.domain.review.publicapi.web.dto.response;

import com.api.trekkey.domain.review.entity.ReviewAssignment;
import com.api.trekkey.domain.review.entity.ReviewAssignmentStatus;
import java.time.LocalDateTime;
import java.util.List;

public record ReviewSheetAssignmentRes(
        Long assignmentId,
        String submissionPublicId,
        String submissionTitle,
        ReviewAssignmentStatus status,
        LocalDateTime dueAt,
        LocalDateTime completedAt,
        List<ReviewSheetFileRes> files
) {
    public static ReviewSheetAssignmentRes from(
            ReviewAssignment assignment,
            List<ReviewSheetFileRes> files
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
                assignment.getCompletedAt(),
                files == null ? List.of() : List.copyOf(files)
        );
    }
}
