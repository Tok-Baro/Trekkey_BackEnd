package com.api.trekkey.domain.credential.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SignatureApprovalRequest(
        @NotBlank(message = "signatureHex는 필수입니다.")
        @Pattern(
                regexp = "^0x[0-9a-fA-F]{130}$",
                message = "signatureHex는 0x로 시작하는 65바이트 서명이어야 합니다.")
        String signatureHex) {
}
