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
        String disclaimer) {

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
