package com.api.trekkey.domain.review.web.dto.response;

import com.api.trekkey.domain.review.entity.ContestJudge;
import java.time.LocalDateTime;
import java.util.List;

public record ReviewSheetRes(
        String judgeName,
        String roleLabel,
        String contestPublicId,
        String contestTitle,
        LocalDateTime tokenExpiresAt,
        List<ReviewSheetStageRes> stages
) {
    public static ReviewSheetRes of(
            ContestJudge judge,
            List<ReviewSheetStageRes> stages
    ) {
        return new ReviewSheetRes(
                judge.getName(),
                judge.getRoleLabel(),
                judge.getContest().getPublicId(),
                judge.getContest().getTitle(),
                judge.getTokenExpiresAt(),
                stages
        );
    }
}
