package com.api.trekkey.domain.credential.service.support;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public final class UtcTime {

    private UtcTime() {
    }

    public static LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public static Instant toInstant(LocalDateTime localDateTime) {
        return localDateTime.toInstant(ZoneOffset.UTC);
    }

    /**
     * MySQL DATETIME(6) preserves microseconds, while Java Instant can carry nanoseconds.
     * Compare duplicated timestamp claims only at the precision the persistence layer retains.
     */
    public static boolean samePersistedInstant(Instant left, Instant right) {
        return Objects.equals(toPersistencePrecision(left), toPersistencePrecision(right));
    }

    private static Instant toPersistencePrecision(Instant instant) {
        return instant == null ? null : instant.truncatedTo(ChronoUnit.MICROS);
    }
}
