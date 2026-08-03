package com.api.trekkey.domain.review.admin.web.dto.response;

import com.api.trekkey.domain.review.entity.ReviewRound;
import com.api.trekkey.domain.review.entity.ReviewRoundEntry;
import com.api.trekkey.domain.review.entity.ReviewRoundStatus;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record ReviewRoundFinalizeRes(
        Long reviewRoundId,
        ReviewRoundStatus status,
        LocalDateTime finalizedAt,
        List<ReviewRoundEntryRes> entries
) {

    public static ReviewRoundFinalizeRes from(
            ReviewRound round,
            List<ReviewRoundEntry> entries
    ) {
        return new ReviewRoundFinalizeRes(
                round.getId(),
                round.getStatus(),
                round.getFinalizedAt(),
                entries.stream()
                        .sorted(Comparator
                                .comparing(
                                        ReviewRoundEntry::getRankNo,
                                        Comparator.nullsLast(Integer::compareTo))
                                .thenComparing(
                                        ReviewRoundEntry::getId,
                                        Comparator.nullsLast(Long::compareTo)))
                        .map(ReviewRoundEntryRes::from)
                        .toList()
        );
    }
}
