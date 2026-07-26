package com.api.trekkey.domain.review.admin.web.dto;

import com.api.trekkey.domain.review.entity.EntryStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EntryDecisionReq(
        @NotNull(message = "판정 상태를 선택해주세요.")
        EntryStatus status,

        @NotBlank(message = "판정 사유를 입력해주세요.")
        String reason
) {
}
