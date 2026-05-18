package com.memmcol.hes.domain.profile.mappers;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Shared parsing for DLMS profile row value positions (event logs and household token events).
 */
public final class EventRowValueParser {

    private EventRowValueParser() {
    }

    public static LocalDateTime parseEventTime(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (val instanceof Instant instant) {
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        }
        if (val instanceof String s) {
            try {
                return LocalDateTime.parse(s);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    public static Integer parseEventCode(Object rawCode) {
        if (rawCode == null) {
            return null;
        }
        if (rawCode instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(rawCode.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Double parseDoubleValue(Object raw) {
        if (raw == null || raw.toString().isBlank()) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(raw.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String parseStringValue(Object raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
