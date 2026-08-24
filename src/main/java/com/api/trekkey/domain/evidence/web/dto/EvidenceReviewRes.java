package com.api.trekkey.domain.evidence.web.dto;

import com.api.trekkey.domain.evidence.entity.EvidenceTypes.AssuranceLevel;
import com.api.trekkey.domain.evidence.entity.EvidenceTypes.ReviewResult;
import com.api.trekkey.domain.evidence.entity.EvidenceTypes.VerificationCaseStatus;
import com.api.trekkey.domain.evidence.entity.EvidenceTypes.VerificationDecisionType;
import java.time.LocalDateTime;

public record EvidenceReviewRes(
        String casePublicId,
        VerificationCaseStatus caseStatus,
        int reviewCount,
        ReviewResult submittedResult,
        VerificationDecisionType finalDecision,
        AssuranceLevel assuranceLevel,
        LocalDateTime reviewedAt) {}
