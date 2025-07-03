package com.memmcol.hes.service;

import gurux.dlms.GXByteBuffer;
import gurux.dlms.GXDateTime;
import gurux.dlms.enums.DataType;
import gurux.dlms.internal.GXCommon;
import gurux.dlms.internal.GXDataInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class DlmsDateUtils {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static ParsedTimestamp parseTimestamp(Object val, int columnIndex) {
        try {
            if (val instanceof GXDateTime dt) {
                LocalDateTime ldt = dt.getMeterCalendar()
                        .toInstant().atZone(ZoneId.systemDefault())
                        .toLocalDateTime();
                return new ParsedTimestamp(ldt, formatter.format(ldt));

            } else if (val instanceof byte[] b) {
                try {
                    GXDateTime dt;

                    if (b.length == 12 && !(b[0] == 0x09 && b[1] == 0x0C)) {
                        // Manually wrap as tagged OctetString: 0x09 0x0C + 12 bytes
                        GXByteBuffer buffer = new GXByteBuffer(b); // Raw datetime bytes

                        GXDataInfo info = new GXDataInfo();
                        info.setType(DataType.DATETIME);
                        Object parsed = GXCommon.getData(null, buffer, info);

                        if (parsed instanceof GXDateTime d) {
                            dt = d;
                        } else {
                            throw new IllegalArgumentException("Parsed value is not GXDateTime");
                        }

                    } else if (b.length >= 2 && b[0] == 0x09 && b[1] == 0x0C) {
                        // Already tagged
                        dt = GXCommon.getDateTime(b);

                    } else {
                        throw new IllegalArgumentException("Unsupported timestamp byte[] format.");
                    }

                    LocalDateTime ldt = dt.getMeterCalendar()
                            .toInstant().atZone(ZoneId.systemDefault())
                            .toLocalDateTime();
                    return new ParsedTimestamp(ldt, formatter.format(ldt));

                } catch (Exception ex) {
                    log.warn("⚠️ Failed to decode byte[] timestamp at column {}: {}", columnIndex, ex.getMessage());
                    return null;
                }

            } else {
                log.warn("⚠️ Unexpected timestamp format at column {}: {}", columnIndex, val.getClass());
                return null;
            }

        } catch (Exception ex) {
            log.warn("🔥 Unhandled timestamp exception at column {}: {}", columnIndex, ex.toString());
            return null;
        }
    }

    public static class ParsedTimestamp {
        public final LocalDateTime dateTime;
        public final String formatted;

        public ParsedTimestamp(LocalDateTime dateTime, String formatted) {
            this.dateTime = dateTime;
            this.formatted = formatted;
        }
    }
}
