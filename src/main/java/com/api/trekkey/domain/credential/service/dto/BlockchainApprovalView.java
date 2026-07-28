package com.api.trekkey.domain.credential.service.dto;

import java.time.Instant;

public record BlockchainApprovalView(
        String aggregateType,
        String aggregateId,
        String typedDataJson,
        String digestHex,
        long approvalNonce,
        Instant deadline) {
}
