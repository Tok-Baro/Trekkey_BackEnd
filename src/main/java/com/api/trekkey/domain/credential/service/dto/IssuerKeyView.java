package com.api.trekkey.domain.credential.service.dto;

import com.api.trekkey.domain.credential.entity.IssuerKeyStatus;
import java.time.Instant;

public record IssuerKeyView(
        int keyVersion,
        String signerAddress,
        IssuerKeyStatus status,
        Instant validFrom,
        Instant validUntil,
        Instant compromisedAt) {
}
