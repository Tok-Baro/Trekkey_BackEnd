package com.api.trekkey.domain.invitation.web.dto.request;

import jakarta.validation.constraints.NotNull;

public record AdminApprovalReq(
        @NotNull(message = "승인 여부는 필수입니다")
        Boolean approve
) {
}
