package com.api.trekkey.domain.review.admin.web.dto.response;

import com.api.trekkey.domain.review.entity.ReviewCriterion;
import com.api.trekkey.domain.review.entity.ReviewRound;
import com.api.trekkey.domain.review.entity.ReviewRoundDecisionRule;
import com.api.trekkey.domain.review.entity.ReviewRoundStatus;
import com.api.trekkey.domain.review.entity.ReviewRoundTargetType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record ReviewRoundRes(
        Long id,
        int roundNo,
        String name,
        ReviewRoundStatus status,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        ReviewRoundTargetType targetType,
        ReviewRoundDecisionRule decisionRule,
        Integer selectCount,
        BigDecimal minScore,
        LocalDateTime finalizedAt,
        List<ReviewRoundCriterionRes> criteria,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ReviewRoundRes from(
            ReviewRound round,
            List<ReviewCriterion> criteria
    ) {
        return new ReviewRoundRes(
                round.getId(),
                round.getRoundNo(),
                round.getName(),
                round.getStatus(),
                round.getStartsAt(),
                round.getEndsAt(),
                round.getTargetType(),
                round.getDecisionRule(),
                round.getSelectCount(),
                round.getMinScore(),
                round.getFinalizedAt(),
                criteria.stream()
                        .filter(ReviewCriterion::isActive)
                        .sorted(Comparator
                                .comparingInt(ReviewCriterion::getSortOrder)
                                .thenComparing(
                                        ReviewCriterion::getId,
                                        Comparator.nullsLast(Long::compareTo)))
                        .map(ReviewRoundCriterionRes::from)
                        .toList(),
                round.getCreatedAt(),
                round.getUpdatedAt()
        );
    }
}
