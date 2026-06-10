package com.memmcol.hes.domain.network;

import com.memmcol.hes.application.port.out.MeterLockPort;
import com.memmcol.hes.dto.MeterDTO;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.model.DlmsResponse;
import com.memmcol.hes.nettyUtils.SessionManagerMultiVendor;
import com.memmcol.hes.repository.MeterRepository;
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
    private final MeterRepository meterRepository;

    /**
     * Write APN value into the meter.
     * MD: OBIS: 0.0.25.4.0.255 (Class 45 - GPRS Setup, Attribute 2 - APN)
     * Non-MD: OBIS: 0.11.25.4.0.255 (Class 45 - GPRS Setup, Attribute 2 - APN)
     */
    public Map<String, Object> writeApn(String meterSerial, String apn) throws Exception {
        return meterLockPort.withExclusive(meterSerial, () -> {
            GXDLMSClient client = sessionManager.getOrCreateClient(meterSerial);
            if (client == null) {
                throw new IllegalStateException("No DLMS session found for meter: " + meterSerial);
            }

            MeterDTO meter = meterRepository.findMeterDetailsByMeterNumber(meterSerial)
                    .orElseThrow(() -> new IllegalArgumentException("Meter not found: " + meterSerial));
            boolean isMd = "MD".equalsIgnoreCase(meter.getMeterClass());
            String obis = isMd ? "0.0.25.4.0.255" : "0.11.25.4.0.255";

            log.info("Writing APN '{}' to meter {} (Class: {}, OBIS: {})", apn, meterSerial, meter.getMeterClass(), obis);

            // Class 45 (GPRS Setup), Attribute 2 (APN) is Octet String (DataType.OCTET_STRING)
            DlmsResponse response = dlmsReaderUtils.writeAttribute(client, meterSerial, obis, 45, 2,
                    apn.getBytes(StandardCharsets.UTF_8), DataType.OCTET_STRING);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("meterSerial", meterSerial);
            result.put("status", response.isSuccess() ? "success" : "failed");
            result.put("dlmsStatus", response.getStatus());
            result.put("message", response.getMessage());
            result.put("apn", apn);

            if (response.isSuccess()) {
                log.info("✅ APN written successfully to meter {}", meterSerial);
            } else {
                log.error("❌ Failed to write APN to meter {}: {} ({})", meterSerial, response.getMessage(), response.getStatus());
            }
            return result;
        });
    }

    /**
     * Write IP Address and Port into the meter.
     * MD: OBIS: 0.0.2.1.0.255 (Class 29 - Auto Connect, Attribute 6 - Destination List)
     * Non-MD:
     * - Port: OBIS: 0.11.25.0.0.255 (Class 41 - TCP/UDP Setup, Attribute 2 - Port)
     * - IP: OBIS: 0.11.25.4.0.255 (Class 45 - GPRS Setup, Attribute 5 - IP Address)
     */
    public Map<String, Object> writeIpPort(String meterSerial, List<String> ipPorts) throws Exception {
        return meterLockPort.withExclusive(meterSerial, () -> {
            GXDLMSClient client = sessionManager.getOrCreateClient(meterSerial);
            if (client == null) {
                throw new IllegalStateException("No DLMS session found for meter: " + meterSerial);
            }

            MeterDTO meter = meterRepository.findMeterDetailsByMeterNumber(meterSerial)
                    .orElseThrow(() -> new IllegalArgumentException("Meter not found: " + meterSerial));
            boolean isMd = "MD".equalsIgnoreCase(meter.getMeterClass());

            DlmsResponse response;
            if (isMd) {
                log.info("Writing Destination List {} to MD meter {}", ipPorts, meterSerial);

                // Class 29 (Auto Connect), Attribute 6 (Destination List) is an Array of Octet Strings
                List<byte[]> octetStrings = ipPorts.stream()
                        .map(s -> s.getBytes(StandardCharsets.UTF_8))
                        .toList();

                response = dlmsReaderUtils.writeAttribute(client, meterSerial, "0.0.2.1.0.255", 29, 6,
                        octetStrings, DataType.ARRAY);
            } else {
                // Non-MD logic: Expecting "IP:Port" format in the first element of ipPorts
                String ipPortStr = ipPorts.get(0);
                String[] parts = ipPortStr.split(":");
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Invalid IP:Port format for Non-MD meter: " + ipPortStr);
                }
                String ip = parts[0];
                String portStr = parts[1]; // Keep as string for string-based port configuration

                log.info("Writing Port {} then IP {} to Non-MD meter {}", portStr, ip, meterSerial);

                // Fixed: Convert port string to raw UTF-8/ASCII bytes and use DataType.OCTET_STRING (Tag 0x09)
                byte[] portBytes = portStr.getBytes(StandardCharsets.UTF_8);
                response = dlmsReaderUtils.writeAttribute(client, meterSerial, "0.11.25.0.0.255", 41, 2,
                        portBytes, DataType.OCTET_STRING);

                if (response.isSuccess()) {
                    response = dlmsReaderUtils.writeAttribute(client, meterSerial, "0.11.25.4.0.255", 45, 5,
                            ip.getBytes(StandardCharsets.UTF_8), DataType.OCTET_STRING);
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("meterSerial", meterSerial);
            result.put("status", response.isSuccess() ? "success" : "failed");
            result.put("dlmsStatus", response.getStatus());
            result.put("message", response.getMessage());
            result.put("ipPorts", ipPorts);

            if (response.isSuccess()) {
                log.info("✅ Destination List written successfully to meter {}", meterSerial);
            } else {
                log.error("❌ Failed to write Destination List to meter {}: {} ({})", meterSerial, response.getMessage(), response.getStatus());
            }
            return result;
        });
    }
}