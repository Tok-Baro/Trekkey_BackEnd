package com.api.trekkey.domain.review.admin.web.dto.request;

import com.api.trekkey.domain.review.entity.ReviewRoundEntryStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ReviewManualDecisionReq(
        @NotNull Long entryId,
        @NotNull ReviewRoundEntryStatus status,
        @NotBlank @Size(max = 2000) String reason,
        @Positive Integer rankNo
) {

    public ReviewManualDecisionReq(
            Long entryId,
            ReviewRoundEntryStatus status,
            String reason
    ) {
        this(entryId, status, reason, null);
    }
}
