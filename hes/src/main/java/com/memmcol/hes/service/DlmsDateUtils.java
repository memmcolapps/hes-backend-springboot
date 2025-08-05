package com.memmcol.hes.service;

import gurux.dlms.GXByteBuffer;
import gurux.dlms.GXDateTime;
import gurux.dlms.enums.DataType;
import gurux.dlms.internal.GXCommon;
import gurux.dlms.internal.GXDataInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.TimeZone;

@Service
@Slf4j
public class DlmsDateUtils {
    public static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

//    public static ParsedTimestamp parseTimestamp(Object val, int columnIndex) {
//        try {
//            if (val instanceof GXDateTime dt) {
//                LocalDateTime ldt = dt.getMeterCalendar()
//                        .toInstant().atZone(ZoneId.systemDefault())
//                        .toLocalDateTime();
//                return new ParsedTimestamp(ldt, formatter.format(ldt));
//
//            } else if (val instanceof byte[] b) {
//                try {
//                    GXDateTime dt;
//
//                    if (b.length == 12 && !(b[0] == 0x09 && b[1] == 0x0C)) {
//                        // Manually wrap as tagged OctetString: 0x09 0x0C + 12 bytes
//                        GXByteBuffer buffer = new GXByteBuffer(b); // Raw datetime bytes
//
//                        GXDataInfo info = new GXDataInfo();
//                        info.setType(DataType.DATETIME);
//                        Object parsed = GXCommon.getData(null, buffer, info);
//
//                        if (parsed instanceof GXDateTime d) {
//                            dt = d;
//                        } else {
//                            throw new IllegalArgumentException("Parsed value is not GXDateTime");
//                        }
//
//                    } else if (b.length >= 2 && b[0] == 0x09 && b[1] == 0x0C) {
//                        // Already tagged
//                        dt = GXCommon.getDateTime(b);
//
//                    } else {
//                        throw new IllegalArgumentException("Unsupported timestamp byte[] format.");
//                    }
//
//                    LocalDateTime ldt = dt.getMeterCalendar()
//                            .toInstant().atZone(ZoneId.systemDefault())
//                            .toLocalDateTime();
//                    return new ParsedTimestamp(ldt, formatter.format(ldt));
//
//                } catch (Exception ex) {
//                    log.warn("⚠️ Failed to decode byte[] timestamp at column {}: {}", columnIndex, ex.getMessage());
//                    return null;
//                }
//
//            } else {
//                log.warn("⚠️ Unexpected timestamp format at column {}: {}", columnIndex, val.getClass());
//                return null;
//            }
//
//        } catch (Exception ex) {
//            log.warn("🔥 Unhandled timestamp exception at column {}: {}", columnIndex, ex.toString());
//            return null;
//        }
//    }
//
//    public static class ParsedTimestamp {
//        public final LocalDateTime dateTime;
//        public final String formatted;
//
//        public ParsedTimestamp(LocalDateTime dateTime, String formatted) {
//            this.dateTime = dateTime;
//            this.formatted = formatted;
//        }
//    }


    /**
     * Parse a DLMS/COSEM timestamp value into LocalDateTime.
     *
     * Supports:
     *  - GXDateTime
     *  - Tagged OctetString (0x09 0x0C ... 12 bytes)
     *  - Raw 12-byte DLMS datetime array
     *
     * Returns null on failure.
     */
    public static LocalDateTime parseTimestampLdt(Object val) {
        try {
            if (val == null) {
                return null;
            }

            // --- GXDateTime case ---
            if (val instanceof GXDateTime gxdt) {
                return gxDateTimeToLdt(gxdt, TimeZone.getDefault().toZoneId());
            }

            // --- byte[] case ---
            if (val instanceof byte[] b) {
                return parseDlmsDateBytesToLdt(b, TimeZone.getDefault().toZoneId());
            }

            // --- String fallback (already formatted) ---
            if (val instanceof String s) {
                try {
                    return LocalDateTime.parse(s, formatter);
                } catch (Exception ignore) {
                    // fall through to warn below
                }
            }

            log.warn("⚠️ Unexpected timestamp type : {}", val.getClass().getName());
            return null;

        } catch (Exception ex) {
            log.warn("🔥 Timestamp parse error : {}", ex.toString());
            return null;
        }
    }

//    GXDateTime → LocalDateTime

    private static LocalDateTime gxDateTimeToLdt(GXDateTime gxdt, ZoneId zone) {
        // getValue() returns java.util.Date aligned to meter time/deviation
        Date d = gxdt.getValue();
        if (d == null) return null;
        return d.toInstant().atZone(zone).toLocalDateTime();
    }

//    Decode DLMS 12-byte (tagged or raw)

    private static LocalDateTime parseDlmsDateBytesToLdt(byte[] input, ZoneId defaultZone) {
        if (input == null || input.length == 0) return null;

        byte[] b;

        // Tagged? 0x09 <len> ...
        if (input.length >= 2 && (input[0] & 0xFF) == 0x09) {
            int len = input[1] & 0xFF;
            if (len != 0x0C) {
                log.warn("⚠️ Timestamp OctetString len={} ", len);
            }
            if (input.length < 2 + len) {
                log.warn("⚠️ Timestamp truncated: got {} bytes ", input.length, 2 + len);
                return null;
            }
            b = Arrays.copyOfRange(input, 2, 2 + len);
        } else {
            // Assume raw 12-byte DLMS datetime
            b = input;
        }

        if (b.length < 12) {
            log.warn("⚠️ DLMS datetime too short ({}) ", b.length);
            return null;
        }

        int year   = u16(b[0], b[1]);
        int month  = u8(b[2]);
        int day    = u8(b[3]);
        /* dow   = u8(b[4]); */
        int hour   = u8(b[5]);
        int minute = u8(b[6]);
        int second = u8(b[7]);
        /* hund  = u8(b[8]); */
        int dev    = s16(b[9], b[10]);  // minutes from UTC (DLMS definition)
        /* status = u8(b[11]); */

        // Wildcard handling (0xFF / 0xFFFF)
        if (year   == 0xFFFF) year   = LocalDate.now().getYear();
        if (month  == 0xFF)   month  = 1;
        if (day    == 0xFF)   day    = 1;
        if (hour   == 0xFF)   hour   = 0;
        if (minute == 0xFF)   minute = 0;
        if (second == 0xFF)   second = 0;

        LocalDate date;
        try {
            date = LocalDate.of(year, month, day);
        } catch (Exception e) {
            log.warn("⚠️ Invalid DLMS date Y={} M={} D={} : {}", year, month, day, e.getMessage());
            return null;
        }

        LocalTime time;
        try {
            time = LocalTime.of(hour, minute, second);
        } catch (Exception e) {
            log.warn("⚠️ Invalid DLMS time H={} M={} S={} : {}", hour, minute, second, e.getMessage());
            time = LocalTime.MIDNIGHT;
        }

        ZoneId zone = defaultZone;
        if (dev != (short)0x8000) {
            // DLMS deviation: minutes *behind* UTC → actual offset = -dev
            try {
                zone = ZoneOffset.ofTotalSeconds(-dev * 60);
            } catch (Exception ignore) {}
        }

        return ZonedDateTime.of(date, time, zone)
                .withZoneSameInstant(defaultZone)
                .toLocalDateTime();
    }

    private static int u8(byte v) { return v & 0xFF; }
    private static int u16(byte hi, byte lo) { return ((hi & 0xFF) << 8) | (lo & 0xFF); }
    private static int s16(byte hi, byte lo) { return (short)(((hi & 0xFF) << 8) | (lo & 0xFF)); }

    public static LocalDateTime parseFormattedTimestamp(String formatted) {
        return LocalDateTime.parse(formatted, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
