package com.api.trekkey.domain.review.admin.web.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record ReviewRoundDeadlineExtendReq(
        @NotNull(message = "새 심사 종료 시각은 필수입니다")
        LocalDateTime endsAt
) {
}
