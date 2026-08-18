package com.api.trekkey.domain.evidence.support;

import static com.api.trekkey.domain.evidence.entity.EvidenceTypes.ReviewResult.*;
import static com.api.trekkey.domain.evidence.entity.EvidenceTypes.VerificationDecisionType.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EvidenceReviewConsensusTest {
    @Test void twoApprovalsVerify() { assertThat(EvidenceReviewConsensus.decide(APPROVE, APPROVE)).isEqualTo(VERIFIED); }
    @Test void twoRejectionsReject() { assertThat(EvidenceReviewConsensus.decide(REJECT, REJECT)).isEqualTo(REJECTED); }
    @Test void disagreementNeverVerifies() { assertThat(EvidenceReviewConsensus.decide(APPROVE, REJECT)).isEqualTo(INCONCLUSIVE); }
}
