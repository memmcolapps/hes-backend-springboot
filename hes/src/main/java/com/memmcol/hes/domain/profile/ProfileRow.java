package com.memmcol.hes.domain.profile;

//1. Domain Value Objects & Core Types
/**
 * Immutable row after decoding a profile record.
 */
public record ProfileRow(
        ProfileTimestamp timestamp,
        Double activeKwh,
        Double reactiveKvarh,
        String rawHex // optional debug / trace
) {}