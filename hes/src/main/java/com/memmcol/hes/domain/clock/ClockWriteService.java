package com.memmcol.hes.domain.clock;

import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.nettyUtils.SessionManagerMultiVendor;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXDateTime;
import gurux.dlms.objects.GXDLMSClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClockWriteService {

    private final SessionManagerMultiVendor sessionManager;
    private final DlmsReaderUtils dlmsReaderUtils;

    /**
     * Sets the meter clock (date and time) using the shared DLMS session and DlmsReaderUtils.writeAttribute.
     *
     * @param serial   meter serial number
     * @param dateTime local date-time to set on the meter
     */
    public String setClock(String serial, LocalDateTime dateTime) throws Exception {
        GXDLMSClient client = sessionManager.getOrCreateClient(serial);
        if (client == null) {
            throw new IllegalStateException("No DLMS session found for meter: " + serial);
        }

        GXDLMSClock clock = new GXDLMSClock("0.0.1.0.0.255");

        GXDateTime gxDateTime = new GXDateTime(Date.from(
                dateTime.atZone(ZoneId.systemDefault()).toInstant()
        ));

        dlmsReaderUtils.writeAttribute(client, serial, clock, 2, gxDateTime);

        String formatted = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String message = "🕒 Meter Clock for " + serial + " set to: " + formatted;
        log.info(message);
        return message;
    }

    public String setClockV1(String serial, LocalDateTime dateTime) throws Exception {

        GXDLMSClient client = sessionManager.getOrCreateClient(serial);
        if (client == null) {
            throw new IllegalStateException("No DLMS session found for meter: " + serial);
        }

        GXDLMSClock clock = new GXDLMSClock("0.0.1.0.0.255");

        GXDateTime gxDateTime = new GXDateTime(String.valueOf(dateTime));
        gxDateTime.setDeviation(0); // Explicitly control timezone (adjust if needed)

        log.info("🕒 Initiating clock write → meter={}, targetTime={}", serial, dateTime);

        dlmsReaderUtils.writeAttribute(client, serial, clock, 2, gxDateTime);

        // 🔁 VERIFY (non-negotiable in DLMS writes)
        GXDateTime actual = readClock(client, serial);

        if (!isCloseEnough(actual, gxDateTime)) {
            throw new IllegalStateException(String.format(
                    "Clock verification failed → meter=%s, expected=%s, actual=%s",
                    serial, gxDateTime, actual
            ));
        }

        String formatted = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        log.info("✅ Clock write verified → meter={}, time={}", serial, formatted);

        return "🕒 Meter Clock for " + serial + " set to: " + formatted;
    }

    private boolean isCloseEnough(GXDateTime actual, GXDateTime expected) {

        LocalDateTime a = toLocalDateTime(actual);
        LocalDateTime e = toLocalDateTime(expected);

        long diff = Math.abs(Duration.between(a, e).toSeconds());

        return diff <= 5; // tolerance window (configurable)
    }

    public GXDateTime readClock(GXDLMSClient client, String serial) throws Exception {

        GXDLMSClock clock = new GXDLMSClock("0.0.1.0.0.255");

        Object value = dlmsReaderUtils.readAttribute(client, serial, clock, 2);

        if (!(value instanceof GXDateTime)) {
            throw new IllegalStateException("Invalid clock response → meter=" + serial);
        }

        return (GXDateTime) value;
    }

    private LocalDateTime toLocalDateTime(GXDateTime gxDateTime) {

        Date date = (Date) gxDateTime.getValue();

        return date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }
}

