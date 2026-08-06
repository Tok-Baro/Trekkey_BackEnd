package com.api.trekkey.domain.review.admin.web.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record ReviewAssignmentDueAtReq(
        @NotNull(message = "심사 배정 마감 시각은 필수입니다")
        @Future(message = "심사 배정 마감 시각은 현재보다 이후여야 합니다")
        LocalDateTime dueAt
) {
}
