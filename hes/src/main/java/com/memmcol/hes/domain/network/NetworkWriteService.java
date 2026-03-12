package com.memmcol.hes.domain.network;

import com.memmcol.hes.application.port.out.MeterLockPort;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.nettyUtils.SessionManagerMultiVendor;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.enums.DataType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service to handle network configuration write operations for meters.
 * Following senior engineering practices for DLMS communication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NetworkWriteService {

    private final SessionManagerMultiVendor sessionManager;
    private final DlmsReaderUtils dlmsReaderUtils;
    private final MeterLockPort meterLockPort;

    /**
     * Write APN value into the meter.
     * OBIS: 0.0.25.4.0.255 (Class 45 - GPRS Setup, Attribute 2 - APN)
     */
    public Map<String, Object> writeApn(String meterSerial, String apn) throws Exception {
        return meterLockPort.withExclusive(meterSerial, () -> {
            GXDLMSClient client = sessionManager.getOrCreateClient(meterSerial);
            if (client == null) {
                throw new IllegalStateException("No DLMS session found for meter: " + meterSerial);
            }

            log.info("Writing APN '{}' to meter {}", apn, meterSerial);

            // Class 45 (GPRS Setup), Attribute 2 (APN) is Octet String (DataType.OCTET_STRING)
            dlmsReaderUtils.writeAttribute(client, meterSerial, "0.0.25.4.0.255", 45, 2,
                    apn.getBytes(StandardCharsets.UTF_8), DataType.OCTET_STRING);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("meterSerial", meterSerial);
            result.put("status", "success");
            result.put("apn", apn);

            log.info("✅ APN written successfully to meter {}", meterSerial);
            return result;
        });
    }

    /**
     * Write IP Address and Port (Destination List) into the meter.
     * OBIS: 0.0.2.1.0.255 (Class 29 - Auto Connect, Attribute 6 - Destination List)
     */
    public Map<String, Object> writeIpPort(String meterSerial, List<String> ipPorts) throws Exception {
        return meterLockPort.withExclusive(meterSerial, () -> {
            GXDLMSClient client = sessionManager.getOrCreateClient(meterSerial);
            if (client == null) {
                throw new IllegalStateException("No DLMS session found for meter: " + meterSerial);
            }

            log.info("Writing Destination List {} to meter {}", ipPorts, meterSerial);

            // Class 29 (Auto Connect), Attribute 6 (Destination List) is an Array of Octet Strings
            // Each entry in the destination list is typically an Octet String
            // Converting List<String> to List<byte[]> for Gurux write operation
            List<byte[]> octetStrings = ipPorts.stream()
                    .map(s -> s.getBytes(StandardCharsets.UTF_8))
                    .toList();

            dlmsReaderUtils.writeAttribute(client, meterSerial, "0.0.2.1.0.255", 29, 6,
                    octetStrings, DataType.ARRAY);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("meterSerial", meterSerial);
            result.put("status", "success");
            result.put("ipPorts", ipPorts);

            log.info("✅ Destination List written successfully to meter {}", meterSerial);
            return result;
        });
    }
}
