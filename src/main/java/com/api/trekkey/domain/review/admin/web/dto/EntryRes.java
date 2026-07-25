package com.api.trekkey.domain.review.admin.web.dto;

import com.api.trekkey.domain.review.entity.ContestStageEntry;
import com.api.trekkey.domain.review.entity.DecisionType;
import com.api.trekkey.domain.review.entity.EntryStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EntryRes(
        Long id,
        String submissionId,
        String submissionTitle,
        String teamName,
        EntryStatus status,
        BigDecimal finalScore,
        Integer rankNo,
        DecisionType decisionType,
        LocalDateTime finalizedAt,
        // 심사 진행 현황 — 제출된 심사 수와 현재 평균 (확정 전 참고용)
        long reviewCount,
        BigDecimal averageScore
) {
    public static EntryRes of(ContestStageEntry entry, long reviewCount, BigDecimal averageScore) {
        return new EntryRes(
                entry.getId(),
                entry.getSubmission().getPublicId(),
                entry.getSubmission().getTitle(),
                entry.getSubmission().getTeam().getName(),
                entry.getStatus(),
                entry.getFinalScore(),
                entry.getRankNo(),
                entry.getDecisionType(),
                entry.getFinalizedAt(),
                reviewCount,
                averageScore
        );
    }
}
