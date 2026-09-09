package com.api.trekkey.domain.graduation.web.dto;

import com.api.trekkey.domain.graduation.entity.GraduationTypes.EvaluationStatus;
import com.api.trekkey.domain.graduation.entity.GraduationTypes.RequirementStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record GraduationEvaluationRes(
        String evaluationPublicId,
        EvaluationStatus status,
        LocalDate policyAsOf,
        LocalDateTime evaluatedAt,
        Summary summary,
        List<AppliedPolicy> policies,
        List<RequirementResult> requirements,
        String disclaimer,
        Coverage coverage) {

    public GraduationEvaluationRes(String evaluationPublicId, EvaluationStatus status,
            LocalDate policyAsOf, LocalDateTime evaluatedAt, Summary summary,
            List<AppliedPolicy> policies, List<RequirementResult> requirements, String disclaimer) {
        this(evaluationPublicId, status, policyAsOf, evaluatedAt, summary, policies, requirements, disclaimer,
                new Coverage(false, List.of(), List.of(), List.of(),
                        List.of(new CoverageGap("COVERAGE_UNAVAILABLE", "평가 범위 정보가 없어 전체 충족을 확인할 수 없습니다."))));
    }

    public record Coverage(boolean complete, List<String> appliedPolicyTypes,
            List<String> missingPolicyTypes, List<String> uncoveredUnitPublicIds, List<CoverageGap> gaps) {}
    public record CoverageGap(String code, String message) {}

    public record Summary(int satisfied, int unsatisfied, int unknown) {}
    public record AppliedPolicy(String publicId, String policyCode, int version, String title) {}
    public record RequirementResult(
            String code,
            String title,
            RequirementStatus status,
            String currentValue,
            String requiredValue,
            String remainingValue,
            String message,
            Source source) {}
    public record Source(String title, String url, String locator) {}
}
