package com.api.trekkey.domain.review.web.dto.response;

import com.api.trekkey.domain.review.entity.ReviewCriterion;

public record ReviewRoundCriterionRes(
        Long id,
        String code,
        String label,
        int maxScore,
        int sortOrder,
        boolean active
) {
    public static ReviewRoundCriterionRes from(ReviewCriterion criterion) {
        return new ReviewRoundCriterionRes(
                criterion.getId(),
                criterion.getCode(),
                criterion.getLabel(),
                criterion.getMaxScore(),
                criterion.getSortOrder(),
                criterion.isActive()
        );
    }
}
