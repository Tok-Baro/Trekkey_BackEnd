package com.api.trekkey.domain.review.admin.web.dto;

import com.api.trekkey.domain.review.entity.ContestJudge;
import java.time.LocalDateTime;

public record JudgeRes(
        Long id,
        String name,
        String roleLabel,
        LocalDateTime tokenExpiresAt,
        long assignedCount,
        long completedCount,
        // 심사 링크 — 발급/재발급 응답에만 값 존재 (원문 토큰 포함, 저장·로그 금지)
        String reviewUrl
) {
    public static JudgeRes of(ContestJudge judge, long assignedCount, long completedCount, String reviewUrl) {
        return new JudgeRes(
                judge.getId(),
                judge.getName(),
                judge.getRoleLabel(),
                judge.getTokenExpiresAt(),
                assignedCount,
                completedCount,
                reviewUrl
        );
    }
}
