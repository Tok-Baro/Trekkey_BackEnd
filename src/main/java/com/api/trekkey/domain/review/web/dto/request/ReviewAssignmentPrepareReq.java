package com.api.trekkey.domain.review.web.dto.request;

import jakarta.validation.constraints.Future;
import java.time.LocalDateTime;

public record ReviewAssignmentPrepareReq(
        @Future(message = "심사 배정 마감 시각은 현재보다 이후여야 합니다")
        LocalDateTime dueAt
) {
}
