package com.api.trekkey.domain.review.web.dto.response;

import com.api.trekkey.domain.review.entity.Review;
import com.api.trekkey.domain.review.entity.ReviewScoreItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record ReviewSubmitRes(
        Long reviewId,
        Long assignmentId,
        BigDecimal totalScore,
        String comment,
        LocalDateTime submittedAt,
        List<ReviewScoreItemRes> scores
) {
    public static ReviewSubmitRes of(
            Review review,
            List<ReviewScoreItem> scoreItems
    ) {
        List<ReviewScoreItemRes> scores = scoreItems.stream()
                .sorted(Comparator
                        .comparingInt((ReviewScoreItem item) ->
                                item.getReviewCriterion().getSortOrder())
                        .thenComparing(item ->
                                item.getReviewCriterion().getId()))
                .map(ReviewScoreItemRes::from)
                .toList();
        return new ReviewSubmitRes(
                review.getId(),
                review.getAssignment().getId(),
                review.getTotalScore(),
                review.getComment(),
                review.getSubmittedAt(),
                scores
        );
    }
}
