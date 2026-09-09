package com.api.trekkey.domain.credential.service.support;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UtcTimeTest {
    @Test
    void reproducesActualSyntheticMySqlWorkTimestampRounding() {
        assertThat(UtcTime.samePersistedInstant(Instant.parse("2026-09-08T12:32:29.868396862Z"),
                Instant.parse("2026-09-08T12:32:29.868397Z"))).isTrue();
    }

    @Test
    void keepsObservedParticipationAndAwardTruncationMatches() {
        assertThat(UtcTime.samePersistedInstant(Instant.parse("2026-09-08T12:00:00.718983199Z"),
                Instant.parse("2026-09-08T12:00:00.718983Z"))).isTrue();
        assertThat(UtcTime.samePersistedInstant(Instant.parse("2026-09-08T12:00:00.492097432Z"),
                Instant.parse("2026-09-08T12:00:00.492097Z"))).isTrue();
    }

    @Test
    void allowsOnlyExactMicrosecondFloorOrHalfUpRepresentationNotAnEpsilon() {
        Instant base = Instant.parse("2026-09-08T12:00:00.123456Z");
        for (int nanos : new int[] {0, 1, 499, 500, 862, 999}) {
            Instant payload = base.plusNanos(nanos);
            assertThat(UtcTime.samePersistedInstant(payload, base)).isTrue();
            assertThat(UtcTime.samePersistedInstant(payload, base.plusNanos(1000))).isEqualTo(nanos >= 500);
            assertThat(UtcTime.samePersistedInstant(payload, base.minusNanos(1000))).isFalse();
            assertThat(UtcTime.samePersistedInstant(payload, base.plusNanos(2000))).isFalse();
        }
    }

    @Test
    void rejectsNonAlignedMetadataExceptExactOriginalInstant() {
        Instant payload = Instant.parse("2026-09-08T12:00:00.123456862Z");
        assertThat(UtcTime.samePersistedInstant(payload, payload)).isTrue();
        assertThat(UtcTime.samePersistedInstant(payload, payload.plusNanos(1))).isFalse();
        assertThat(UtcTime.samePersistedInstant(payload, payload.minusNanos(1))).isFalse();
    }

    @Test
    void carriesRoundingAcrossSecondDayAndYearBoundaries() {
        assertThat(UtcTime.samePersistedInstant(Instant.parse("2026-12-31T23:59:59.999999862Z"),
                Instant.parse("2027-01-01T00:00:00Z"))).isTrue();
        assertThat(UtcTime.samePersistedInstant(Instant.parse("2026-12-31T23:59:59.999999499Z"),
                Instant.parse("2027-01-01T00:00:00Z"))).isFalse();
    }

    @Test
    void rejectsOneMicrosecondChangeWhenCanonicalHasNoExcessPrecision() {
        Instant payload = Instant.parse("2026-09-08T12:00:00.123456Z");
        assertThat(UtcTime.samePersistedInstant(payload, payload.plusNanos(1000))).isFalse();
        assertThat(UtcTime.samePersistedInstant(payload, payload.minusNanos(1000))).isFalse();
    }

    @Test
    void nullExpiryMatchesOnlyNull() {
        assertThat(UtcTime.samePersistedInstant(null, null)).isTrue();
        assertThat(UtcTime.samePersistedInstant(Instant.EPOCH, null)).isFalse();
        assertThat(UtcTime.samePersistedInstant(null, Instant.EPOCH)).isFalse();
    }
}
