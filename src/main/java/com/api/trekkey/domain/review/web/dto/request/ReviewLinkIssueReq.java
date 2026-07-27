package com.api.trekkey.domain.review.web.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record ReviewLinkIssueReq(
        @NotNull(message = "심사 링크 만료 시각은 반드시 입력해야 합니다")
        @Future(message = "심사 링크 만료 시각은 현재보다 이후여야 합니다")
        LocalDateTime expiresAt
) {
}
