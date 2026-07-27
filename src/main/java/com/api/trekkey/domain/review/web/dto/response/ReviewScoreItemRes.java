package com.api.trekkey.domain.review.web.dto.response;

import com.api.trekkey.domain.contest.entity.ReviewCriterion;
import com.api.trekkey.domain.review.entity.ReviewScoreItem;
import java.math.BigDecimal;

public record ReviewScoreItemRes(
        Long criterionId,
        String code,
        String label,
        int maxScore,
        int sortOrder,
        BigDecimal score
) {
    public static ReviewScoreItemRes from(ReviewScoreItem item) {
        ReviewCriterion criterion = item.getReviewCriterion();
        return new ReviewScoreItemRes(
                criterion.getId(),
                criterion.getCode(),
                criterion.getLabel(),
                criterion.getMaxScore(),
                criterion.getSortOrder(),
                item.getScore()
        );
    }
}
