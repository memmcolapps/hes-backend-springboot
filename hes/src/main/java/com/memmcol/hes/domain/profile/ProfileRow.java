package com.memmcol.hes.domain.profile;

//1. Domain Value Objects & Core Types

import lombok.Builder;

/**
 * Immutable row after decoding a profile record.
 */
@Builder
public record ProfileRow(
        ProfileTimestamp timestamp,
        Double activeKwh,
        Double reactiveKvarh,
        String rawHex // optional debug / trace
) {}