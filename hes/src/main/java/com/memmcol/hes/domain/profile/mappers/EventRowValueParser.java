package com.memmcol.hes.domain.profile.mappers;

import com.memmcol.hes.infrastructure.dlms.DlmsDataDecoder;

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
        if (raw instanceof String s) {
            String trimmed = s.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        if (raw instanceof byte[] bytes) {
            return octetStringToText(bytes);
        }
        if (raw instanceof Number number) {
            return number.toString();
        }
        if (raw.getClass().isArray() && raw.getClass().getComponentType() == byte.class) {
            return octetStringToText((byte[]) raw);
        }
        String s = raw.toString().trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * DLMS octet strings (tokens, IDs, types) arrive as {@code byte[]}; {@link Object#toString()} yields {@code [B@...}.
     */
    private static String octetStringToText(byte[] bytes) {
        Object decoded = DlmsDataDecoder.decodeOctetString(bytes);
        if (decoded == null) {
            return null;
        }
        String text = decoded.toString().trim();
        return text.isEmpty() ? null : text;
    }
}
