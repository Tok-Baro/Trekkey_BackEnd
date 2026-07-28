package com.api.trekkey.domain.review.publicapi.web.dto.response;

import com.api.trekkey.domain.review.entity.ContestJudge;
import java.time.LocalDateTime;
import java.util.List;

public record ReviewSheetRes(
        String judgeName,
        String roleLabel,
        String contestPublicId,
        String contestTitle,
        LocalDateTime tokenExpiresAt,
        List<ReviewSheetRoundRes> rounds
) {
    public static ReviewSheetRes of(
            ContestJudge judge,
            List<ReviewSheetRoundRes> rounds
    ) {
        return new ReviewSheetRes(
                judge.getName(),
                judge.getRoleLabel(),
                judge.getContest().getPublicId(),
                judge.getContest().getTitle(),
                judge.getTokenExpiresAt(),
                rounds
        );
    }
}
