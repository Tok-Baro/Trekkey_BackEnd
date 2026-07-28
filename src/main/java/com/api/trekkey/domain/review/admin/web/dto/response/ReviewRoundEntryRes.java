package com.api.trekkey.domain.review.admin.web.dto.response;

import com.api.trekkey.domain.review.entity.ReviewDecisionType;
import com.api.trekkey.domain.review.entity.ReviewRoundEntry;
import com.api.trekkey.domain.review.entity.ReviewRoundEntryStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReviewRoundEntryRes(
        Long id,
        Long reviewRoundId,
        String submissionPublicId,
        String submissionTitle,
        String teamName,
        ReviewRoundEntryStatus status,
        BigDecimal finalScore,
        Integer rankNo,
        ReviewDecisionType decisionType,
        String decisionReason,
        LocalDateTime submissionFinalizedAt,
        LocalDateTime finalizedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ReviewRoundEntryRes from(ReviewRoundEntry entry) {
        return new ReviewRoundEntryRes(
                entry.getId(),
                entry.getReviewRound().getId(),
                entry.getSubmission().getPublicId(),
                entry.getSubmission().getTitle(),
                entry.getSubmission().getTeam().getName(),
                entry.getStatus(),
                entry.getFinalScore(),
                entry.getRankNo(),
                entry.getDecisionType(),
                entry.getDecisionReason(),
                entry.getSubmission().getFinalizedAt(),
                entry.getFinalizedAt(),
                entry.getCreatedAt(),
                entry.getUpdatedAt()
        );
    }
}
