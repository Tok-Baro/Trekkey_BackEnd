package com.api.trekkey.domain.review.web.dto.response;

import com.api.trekkey.domain.contest.entity.ReviewCriterion;

public record ReviewSheetCriterionRes(
        Long id,
        String code,
        String label,
        int maxScore,
        int sortOrder
) {
    public static ReviewSheetCriterionRes from(
            ReviewCriterion criterion
    ) {
        return new ReviewSheetCriterionRes(
                criterion.getId(),
                criterion.getCode(),
                criterion.getLabel(),
                criterion.getMaxScore(),
                criterion.getSortOrder()
        );
    }
}
