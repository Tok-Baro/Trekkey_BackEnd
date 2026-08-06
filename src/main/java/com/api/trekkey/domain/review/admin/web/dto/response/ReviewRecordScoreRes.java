package com.api.trekkey.domain.review.admin.web.dto.response;

import com.api.trekkey.domain.review.entity.ReviewCriterion;
import com.api.trekkey.domain.review.entity.ReviewScoreItem;
import java.math.BigDecimal;

public record ReviewRecordScoreRes(
        Long criterionId,
        String code,
        String label,
        int maxScore,
        int sortOrder,
        BigDecimal score
) {
    public static ReviewRecordScoreRes from(ReviewScoreItem item) {
        ReviewCriterion criterion = item.getReviewCriterion();
        return new ReviewRecordScoreRes(
                criterion.getId(),
                criterion.getCode(),
                criterion.getLabel(),
                criterion.getMaxScore(),
                criterion.getSortOrder(),
                item.getScore());
    }
}
