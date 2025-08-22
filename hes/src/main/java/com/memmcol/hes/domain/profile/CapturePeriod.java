package com.memmcol.hes.domain.profile;


import java.time.Duration;

//1. Domain Value Objects & Core Types
public record CapturePeriod(int seconds) {
    public CapturePeriod {
        if (seconds <= 0) throw new IllegalArgumentException("CapturePeriod must be > 0");
    }

    public Duration asDuration() {
        return Duration.ofSeconds(seconds);
    }
}

