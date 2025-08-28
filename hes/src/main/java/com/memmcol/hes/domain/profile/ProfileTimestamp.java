package com.memmcol.hes.domain.profile;

import java.time.LocalDateTime;


//1. Domain Value Objects & Core Types
public record ProfileTimestamp(LocalDateTime value) implements Comparable<ProfileTimestamp> {
    public ProfileTimestamp {
        if (value == null) throw new IllegalArgumentException("Timestamp cannot be null");
    }

    @Override
    public int compareTo(ProfileTimestamp o) {
        return value.compareTo(o.value());
    }

    public ProfileTimestamp plus(CapturePeriod cp) {
        return new ProfileTimestamp(value.plusSeconds(cp.seconds()));
    }

    public static ProfileTimestamp of(LocalDateTime dt) {
        return new ProfileTimestamp(dt);
    }

    public static ProfileTimestamp ofNullable(LocalDateTime dt) {
        return dt == null ? null : new ProfileTimestamp(dt);
    }

    public static ProfileTimestamp now() {
        return new ProfileTimestamp(LocalDateTime.now());
    }


}
