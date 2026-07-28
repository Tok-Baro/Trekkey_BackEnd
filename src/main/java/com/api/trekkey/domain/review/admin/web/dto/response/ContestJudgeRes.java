package com.api.trekkey.domain.review.admin.web.dto.response;

import com.api.trekkey.domain.review.entity.ContestJudge;
import com.api.trekkey.domain.review.entity.ReviewLinkStatus;
import java.time.LocalDateTime;

public record ContestJudgeRes(
        Long id,
        Long userId,
        String name,
        String roleLabel,
        ReviewLinkStatus reviewLinkStatus,
        LocalDateTime tokenIssuedAt,
        LocalDateTime tokenExpiresAt,
        LocalDateTime tokenRevokedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ContestJudgeRes from(ContestJudge judge, LocalDateTime now) {
        return new ContestJudgeRes(
                judge.getId(),
                judge.getUser() == null ? null : judge.getUser().getId(),
                judge.getName(),
                judge.getRoleLabel(),
                judge.getReviewLinkStatus(now),
                judge.getTokenIssuedAt(),
                judge.getTokenExpiresAt(),
                judge.getTokenRevokedAt(),
                judge.getCreatedAt(),
                judge.getUpdatedAt()
        );
    }
}
