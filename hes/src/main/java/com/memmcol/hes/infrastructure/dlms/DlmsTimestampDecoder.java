package com.memmcol.hes.infrastructure.dlms;

import gurux.dlms.GXDateTime;
import gurux.dlms.internal.GXCommon;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
            if (value instanceof GXDateTime gx) {
                return convertGXDateTime(gx);
            }
            if (value instanceof Date d) {
                return LocalDateTime.ofInstant(d.toInstant(), zone);
            }
            if (value instanceof byte[] raw) {
                // fallback: parse OctetString → GXDateTime
                GXDateTime gx = GXCommon.getDateTime(raw);
                return convertGXDateTime(gx);
            }
            // As last resort, attempt to parse a string
            return LocalDateTime.parse(value.toString());
        } catch (Exception e) {
            log.warn("Failed to decode timestamp from {}: {}", value, e.getMessage());
            return null;
        }
    }

    private LocalDateTime convertGXDateTime(GXDateTime gx) {
        Instant instant = gx.getMeterCalendar().getTime().toInstant();

        // Handle "skipped" fields (GXDateTime supports wildcard values)
//        if (gx.getSkip() != null) {
//            // You might log or adjust here
//            log.debug("Timestamp has skip flags: {}", gx.getSkip());
//        }
        return LocalDateTime.ofInstant(instant, zone);
    }
}
