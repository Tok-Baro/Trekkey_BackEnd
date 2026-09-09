package com.api.trekkey.domain.credential.service.support;

import java.time.Instant;
import java.time.DateTimeException;
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

    /** New duplicated DB metadata is explicitly representable in DATETIME(6); canonical input is unchanged. */
    public static LocalDateTime toPersistedLocalDateTime(Instant instant) {
        return toLocalDateTime(instant.truncatedTo(ChronoUnit.MICROS));
    }

    /**
     * Compare immutable canonical time with its duplicated DB metadata. MySQL DATETIME(6)
     * can round half-up; TIME_TRUNCATE_FRACTIONAL and older writers can truncate instead.
     * Accept ONLY these exact representations, never an arbitrary +/- microsecond tolerance.
     * If metadata still has nanoseconds (e.g. an unflushed entity), only exact equality is valid.
     */
    public static boolean samePersistedInstant(Instant canonical, Instant stored) {
        if (Objects.equals(canonical, stored)) return true;
        if (canonical == null || stored == null || stored.getNano() % 1_000 != 0) return false;
        Instant floor = canonical.truncatedTo(ChronoUnit.MICROS);
        if (floor.equals(stored)) return true;
        if (canonical.getNano() % 1_000 < 500) return false;
        try {
            return floor.plusNanos(1_000).equals(stored);
        } catch (DateTimeException overflow) {
            return false;
        }
    }
}
