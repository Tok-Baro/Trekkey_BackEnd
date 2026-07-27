package com.api.trekkey.domain.credential.web.dto;

import jakarta.validation.constraints.NotBlank;

public record IssuerKeySyncRequest(
        @NotBlank(message = "signerRef는 필수입니다.")
        String signerRef) {
}
