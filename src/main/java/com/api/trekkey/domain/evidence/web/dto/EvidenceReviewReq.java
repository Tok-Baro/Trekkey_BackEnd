package com.api.trekkey.domain.evidence.web.dto;

import com.api.trekkey.domain.evidence.entity.EvidenceTypes.AssuranceLevel;
import com.api.trekkey.domain.evidence.entity.EvidenceTypes.ReviewResult;
import com.api.trekkey.domain.evidence.entity.EvidenceTypes.ReviewReasonCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EvidenceReviewReq(
        @NotNull ReviewResult result,
        @NotNull AssuranceLevel assuranceLevel,
        @NotNull ReviewReasonCode reasonCode,
        @Size(max = 1000) String officialReferenceUrl,
        @Size(max = 1000) String note) {}
