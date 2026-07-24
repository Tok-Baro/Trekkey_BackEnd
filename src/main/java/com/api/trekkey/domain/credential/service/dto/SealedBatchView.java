package com.api.trekkey.domain.credential.service.dto;

import com.api.trekkey.domain.credential.entity.BatchStatus;
import java.time.Instant;

public record SealedBatchView(
        String publicId,
        BatchStatus status,
        int leafCount,
        String merkleRoot,
        Instant approvalDeadline) {
}
