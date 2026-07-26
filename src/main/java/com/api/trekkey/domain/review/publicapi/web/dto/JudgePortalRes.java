package com.api.trekkey.domain.review.publicapi.web.dto;

import java.math.BigDecimal;
import java.util.List;

// 심사위원 포털 초기 데이터 — 토큰 검증 후 배정 목록과 평가 기준을 한 번에 내려준다
public record JudgePortalRes(
        String judgeName,
        String roleLabel,
        String contestTitle,
        List<AssignmentRes> assignments
) {
    public record AssignmentRes(
            Long assignmentId,
            String stageName,
            boolean reviewed,
            SubmissionSummary submission,
            List<CriterionSummary> criteria
    ) {
    }

    public record SubmissionSummary(
            String title,
            String teamName,
            List<FileSummary> files
    ) {
    }

    public record FileSummary(
            Long id,
            String originalName,
            long sizeBytes
    ) {
    }

    public record CriterionSummary(
            Long id,
            String label,
            BigDecimal maxScore
    ) {
    }
}
