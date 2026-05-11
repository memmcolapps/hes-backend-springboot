package com.memmcol.hes.domain.controlRelay;


import com.memmcol.hes.application.port.out.MeterLockPort;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.nettyUtils.SessionManagerMultiVendor;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.enums.DataType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ControlModeService {

    private final SessionManagerMultiVendor sessionManager;
    private final DlmsReaderUtils dlmsReaderUtils;
    private final MeterLockPort meterLockPort;

    public Map<String, Object> setControlMode(String meterSerial, int mode) throws Exception {

        return meterLockPort.withExclusive(meterSerial, () -> {

            GXDLMSClient client = sessionManager.getOrCreateClient(meterSerial);

            if (client == null) {
                throw new IllegalStateException("No DLMS session found for meter: " + meterSerial);
            }

            log.info("Sending raw control mode value {} to meter {}", mode, meterSerial);

            dlmsReaderUtils.writeAttribute(
                    client,
                    meterSerial,
                    "0.0.96.3.10.255",   // OBIS
                    70,                 // Class ID (Disconnect Control)
                    4,                  // Attribute index (control mode)
                    (byte) mode,        // raw enum value
                    DataType.ENUM       // DLMS enum type
            );

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("meterSerial", meterSerial);
            result.put("status", "success");
            result.put("sentMode", mode);

            log.info("Control mode command sent successfully to meter {}", meterSerial);

            return result;
        });
    }
}