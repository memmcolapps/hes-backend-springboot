package com.memmcol.hes.domain.profile;

//1. Domain Value Objects & Core Types
/**
 * Immutable row after decoding a Cahnnel One profile record.
 */
public record ChannelOneRow(
        ProfileTimestamp timestamp,
        Integer powerDown,
        Double apparentEnergy,
        Double totalActivePower,
        Double L1CurrentHarmonic,
        Double L2CurrentHarmonic,
        Double L3CurrentHarmonic,
        Double V1VoltageHarmonic,
        Double V2VoltageHarmonic,
        Double V3VoltageHarmonic
) {
}
