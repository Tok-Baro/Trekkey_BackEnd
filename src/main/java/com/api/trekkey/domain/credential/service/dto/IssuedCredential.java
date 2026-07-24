package com.api.trekkey.domain.credential.service.dto;

import com.api.trekkey.domain.credential.entity.CredentialStatus;
import java.time.Instant;

public record IssuedCredential(
        String publicId,
        String credentialNo,
        CredentialStatus status,
        Instant issuedAt,
        boolean alreadyExisted) {
}
