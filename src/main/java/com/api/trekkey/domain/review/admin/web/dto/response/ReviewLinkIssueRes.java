package com.api.trekkey.domain.review.admin.web.dto.response;

import java.time.LocalDateTime;

public record ReviewLinkIssueRes(
        Long judgeId,
        String reviewUrl,
        LocalDateTime expiresAt
) {
}
