package com.api.trekkey.domain.credential.service.dto;

import com.api.trekkey.domain.credential.entity.ChainTransactionStatus;
import com.api.trekkey.domain.credential.entity.CredentialStatus;
import java.time.Instant;

public record CredentialStatusEventView(
        Long id,
        String credentialPublicId,
        String credentialNo,
        CredentialStatus credentialStatus,
        CredentialStatus previousStatus,
        CredentialStatus nextStatus,
        String reasonCode,
        String reasonDetail,
        Long actorUserId,
        String supersedingCredentialPublicId,
        boolean approved,
        Instant approvalDeadline,
        Instant effectiveAt,
        ChainTransactionStatus transactionStatus,
        String lastErrorCode,
        Instant createdAt
) {
}
