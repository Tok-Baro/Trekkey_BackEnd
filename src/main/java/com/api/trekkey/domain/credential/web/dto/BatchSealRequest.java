package com.api.trekkey.domain.credential.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record BatchSealRequest(
        @NotBlank(message = "schemaProfileId는 필수입니다.")
        String schemaProfileId,
        @Min(value = 1, message = "keyVersion은 1 이상이어야 합니다.")
        int keyVersion) {
}
