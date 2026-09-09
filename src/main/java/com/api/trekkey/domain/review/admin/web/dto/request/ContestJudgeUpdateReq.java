package com.api.trekkey.domain.review.admin.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Linked user identity is deliberately not editable through this endpoint.
public record ContestJudgeUpdateReq(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String roleLabel
) {
}
