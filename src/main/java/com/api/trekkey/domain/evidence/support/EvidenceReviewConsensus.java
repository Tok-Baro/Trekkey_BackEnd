package com.api.trekkey.domain.evidence.support;

import com.api.trekkey.domain.evidence.entity.EvidenceTypes.ReviewResult;
import com.api.trekkey.domain.evidence.entity.EvidenceTypes.VerificationDecisionType;

public final class EvidenceReviewConsensus {
    private EvidenceReviewConsensus() {}

    public static VerificationDecisionType decide(ReviewResult first, ReviewResult second) {
        if (first != second) return VerificationDecisionType.INCONCLUSIVE;
        return first == ReviewResult.APPROVE
                ? VerificationDecisionType.VERIFIED : VerificationDecisionType.REJECTED;
    }
}
