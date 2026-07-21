package com.api.trekkey.domain.contest.web.dto;

import com.api.trekkey.domain.contest.entity.StageStatus;
import jakarta.validation.constraints.NotNull;

public record StageStatusUpdateReq(
        @NotNull(message = "단계 상태는 반드시 선택해야합니다")
        StageStatus status
) {
}
