package com.api.trekkey.domain.evidence.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EvidenceClaimHasherTest {
    @Test
    void producesStableNonPlainLookupHash() {
        EvidenceClaimHasher hasher = new EvidenceClaimHasher("a-secure-test-secret-that-is-longer-than-32-chars");
        String result = hasher.hash("CERT1234");
        assertThat(result).hasSize(64).isEqualTo(hasher.hash("CERT1234")).doesNotContain("CERT1234");
    }

    @Test
    void rejectsShortRuntimeSecret() {
        assertThatThrownBy(() -> new EvidenceClaimHasher("short"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
