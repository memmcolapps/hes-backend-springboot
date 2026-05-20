package com.memmcol.hes.domain.events;

/**
 * Normalizes DLMS OBIS strings for comparison ({@code event_type.obis_code} ↔ profile OBIS).
 */
public final class ObisCodeNormalizer {

    private ObisCodeNormalizer() {
    }

    public static String normalize(String obis) {
        if (obis == null || obis.isBlank()) {
            return "";
        }
        return obis.trim().replace('-', '.');
    }
}
