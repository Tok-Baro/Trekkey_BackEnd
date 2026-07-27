package com.api.trekkey.domain.submission.publicapi.web.dto;

import com.api.trekkey.domain.submission.entity.Submission;
import com.api.trekkey.domain.submission.entity.SubmissionStatus;
import java.time.LocalDateTime;

public record SubmissionRes(
        String publicId,
        String title,
        SubmissionStatus status,
        LocalDateTime finalizedAt,
        LocalDateTime submittedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static SubmissionRes from(Submission submission) {
        return new SubmissionRes(
                submission.getPublicId(),
                submission.getTitle(),
                submission.getStatus(),
                submission.getFinalizedAt(),
                submission.getSubmittedAt(),
                submission.getCreatedAt(),
                submission.getUpdatedAt()
        );
    }
}
