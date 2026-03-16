package com.memmcol.hes.domain.clock;

import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.nettyUtils.SessionManagerMultiVendor;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXDateTime;
import gurux.dlms.objects.GXDLMSClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
}

