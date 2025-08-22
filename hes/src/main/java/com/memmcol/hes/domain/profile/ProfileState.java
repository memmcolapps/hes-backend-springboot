package com.memmcol.hes.domain.profile;

//1. Domain Value Objects & Core Types
/**
 * State persisted per meter/profile to resume timestamp-based reading.
 */
public record ProfileState(
        String meterSerial,
        String profileObis,
        ProfileTimestamp lastTimestamp,
        CapturePeriod capturePeriod
) {
    public ProfileState advanceTo(ProfileTimestamp ts) {
        return new ProfileState(meterSerial, profileObis, ts, capturePeriod);
    }
}