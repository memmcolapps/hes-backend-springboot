package com.memmcol.hes.service;

import gurux.dlms.GXDateTime;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public final class DlmsDateCodec {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DlmsDateCodec() {}

    public record ParsedTs(LocalDateTime dateTime, String formatted) {}

    public static ParsedTs decode(byte[] b, ZoneId defaultZone) {
        if (b == null || b.length < 12) return new ParsedTs(null, null);

        int year   = u16(b[0], b[1]); // big endian
        int month  = u8(b[2]);
        int day    = u8(b[3]);
        /* dayOfWeek = b[4] (ignored) */
        int hour   = u8(b[5]);
        int minute = u8(b[6]);
        int second = u8(b[7]);
        /* hundredths = b[8] */
        int dev    = s16(b[9], b[10]); // minutes from UTC, signed
        /* status = b[11] */

        // Substitute defaults for "not specified" (0xFF)
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
            return new ParsedTs(null, null);
        }

        LocalTime time;
        try {
            time = LocalTime.of(hour, minute, second);
        } catch (Exception e) {
            time = LocalTime.MIDNIGHT;
        }

        ZoneId zone = defaultZone;
        if (dev != (short)0x8000) { // deviation present
            // Note: +dev means "minutes behind UTC" per DLMS spec → actual offset = -dev
            int offsetSeconds = -dev * 60;
            try {
                zone = ZoneOffset.ofTotalSeconds(offsetSeconds);
            } catch (Exception ignored) {}
        }

        LocalDateTime ldt = ZonedDateTime.of(date, time, zone).withZoneSameInstant(defaultZone).toLocalDateTime();
        return new ParsedTs(ldt, ldt.format(FMT));
    }

    private static int u8(byte v) { return v & 0xFF; }
    private static int u16(byte hi, byte lo) { return ((hi & 0xFF) << 8) | (lo & 0xFF); }
    private static int s16(byte hi, byte lo) { return (short)(((hi & 0xFF) << 8) | (lo & 0xFF)); }


    public static ParsedTs parseDlmsTimestampObject(Object o, ZoneId zone) {
        if (o == null) return new ParsedTs(null, null);

        if (o instanceof GXDateTime gxdt) {
            Date d = gxdt.getValue();
            LocalDateTime ldt = d.toInstant().atZone(zone).toLocalDateTime();
            return new ParsedTs(ldt, ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }

        if (o instanceof byte[] b) {
            return DlmsDateCodec.decode(b, zone);
        }

        if (o instanceof String s) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                return new ParsedTs(ldt, s);
            } catch (Exception ignored) {}
        }

        return new ParsedTs(null, null);
    }

}