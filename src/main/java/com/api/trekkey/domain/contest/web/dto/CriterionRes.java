package com.api.trekkey.domain.contest.web.dto;

import com.api.trekkey.domain.contest.entity.ReviewCriterion;

public record CriterionRes(
        Long id,
        String code,
        String label,
        int maxScore,
        int sortOrder,
        boolean active
) {
    public static CriterionRes from(ReviewCriterion criterion) {
        return new CriterionRes(
                criterion.getId(),
                criterion.getCode(),
                criterion.getLabel(),
                criterion.getMaxScore(),
                criterion.getSortOrder(),
                criterion.isActive()
        );
    }
}
