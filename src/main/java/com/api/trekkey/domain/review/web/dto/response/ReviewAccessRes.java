package com.api.trekkey.domain.review.web.dto.response;

import com.api.trekkey.domain.review.entity.ContestJudge;
import java.time.LocalDateTime;

public record ReviewAccessRes(
        String judgeName,
        String roleLabel,
        String contestPublicId,
        String contestTitle,
        LocalDateTime tokenExpiresAt
) {
    public static ReviewAccessRes from(ContestJudge judge) {
        return new ReviewAccessRes(
                judge.getName(),
                judge.getRoleLabel(),
                judge.getContest().getPublicId(),
                judge.getContest().getTitle(),
                judge.getTokenExpiresAt()
        );
    }
}
