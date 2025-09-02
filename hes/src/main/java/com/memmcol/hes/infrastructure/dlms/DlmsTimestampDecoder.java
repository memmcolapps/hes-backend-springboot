package com.memmcol.hes.infrastructure.dlms;

import gurux.dlms.GXByteBuffer;
import gurux.dlms.GXDateTime;
import gurux.dlms.internal.GXCommon;
import gurux.dlms.internal.GXDataInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;

/**
 * Convert DLMS timestamp objects to Java LocalDateTime.
 */
@Component
@Slf4j
public class DlmsTimestampDecoder {
    private final ZoneId zone = ZoneId.systemDefault(); // or meter timezone if known

    public LocalDateTime toLocalDateTime(Object value) {
        if (value == null) return null;
        try {
            switch (value) {
                case GXDateTime gx -> {
                    return convertGXDateTime(gx);
                }
                case Date d -> {
                    return LocalDateTime.ofInstant(d.toInstant(), zone);
                }
                case byte[] raw -> {
                    return decode(raw);
                }
                default -> {
                    log.info("Fallback for unexpected types");
                    return LocalDateTime.parse(value.toString());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to decode timestamp from {} (type={}): {}", value,
                    value.getClass().getName(), e.getMessage());
            return null;
        }
    }
    private LocalDateTime convertGXDateTime(GXDateTime gx) {
        Instant instant = gx.getMeterCalendar().getTime().toInstant();
        return LocalDateTime.ofInstant(instant, zone);
    }

    public LocalDateTime decode(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            throw new IllegalArgumentException("DLMS datetime must be at least 12 bytes");
        }

        if (bytes[0] == 0) {
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(bytes[0]), zone);
        }

        int year = ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
        int month = (bytes[2] & 0xFF) == 0xFF ? 1 : (bytes[2] & 0xFF);
        int day = (bytes[3] & 0xFF) == 0xFF ? 1 : (bytes[3] & 0xFF);

        int hour = (bytes[5] & 0xFF) == 0xFF ? 0 : (bytes[5] & 0xFF);
        int minute = (bytes[6] & 0xFF) == 0xFF ? 0 : (bytes[6] & 0xFF);
        int second = (bytes[7] & 0xFF) == 0xFF ? 0 : (bytes[7] & 0xFF);

        // Time zone deviation in minutes
        int deviation = ((bytes[9] & 0xFF) << 8) | (bytes[10] & 0xFF);
        if (deviation == 0x8000) deviation = 0; // unspecified
        if (deviation > 0x7FFF) deviation -= 0x10000; // signed conversion

        ZoneOffset offset = ZoneOffset.ofTotalSeconds(deviation * 60);
        return LocalDateTime.of(year, month, day, hour, minute, second)
                .atOffset(offset)
                .toLocalDateTime();
    }

    public LocalDateTime decodeTimestamp(Object rawValue){
        LocalDateTime tsInstant;
        switch (rawValue) {
            case GXDateTime gx ->
                tsInstant = convertGXDateTime(gx);
            case LocalDateTime localDateTime ->
                // Already a LocalDateTime
                    tsInstant = localDateTime;
            case String s ->
                // ISO-8601 date string (e.g., 2025-08-01T00:00)
                    tsInstant = LocalDateTime.parse(s);
            case byte[] bytes ->
                // Raw bytes → decode to LocalDateTime
                    tsInstant = decode(bytes);
            default -> {
                // Fallback: unexpected type
                log.warn("Unexpected timestamp type: {}, using fallback",
                        rawValue.getClass().getName());
                tsInstant = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
            }
        }
        return tsInstant;
    }

}
