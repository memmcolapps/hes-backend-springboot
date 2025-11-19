package com.memmcol.hesTraining.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.memmcol.hes.domain.profile.MeterRatios;
import com.memmcol.hes.domain.profile.ProfileRowGeneric;
import com.memmcol.hes.infrastructure.dlms.DlmsDataDecoder;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.infrastructure.dlms.DlmsTimestampDecoder;
import com.memmcol.hes.infrastructure.dlms.DlmsErrorUtils;
import com.memmcol.hes.nettyUtils.SessionManagerMultiVendor;
import com.memmcol.hes.service.MeterRatioService;
import com.memmcol.hesTraining.dto.CaptureObjectsDTO;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXDateTime;
import gurux.dlms.GXReplyData;
import gurux.dlms.enums.DataType;
import gurux.dlms.enums.ObjectType;
import gurux.dlms.internal.GXCommon;
import gurux.dlms.objects.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.memmcol.hes.exception.DlmsDataAccessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.memmcol.hesTraining.services.MeterReadingService.getUnitSymbol;

@Service
@Slf4j
@AllArgsConstructor
public class ProfileReadingServices {
    private final SessionManagerMultiVendor sessionManagerMultiVendor;
    private final DlmsReaderUtils dlmsReaderUtils;
    private final MeterRatioService ratioService;
    private final DlmsTimestampDecoder timestampDecoder;


    // =====================================================================
    // 🧩 PUBLIC METHODS
    // =====================================================================

    /**
     * Profile reading for NON-MD meters (no CT/PT ratio)
     */
//    public String readProfile_NonMD(String meterSerial, String meterModel, String profileObis,
//                                    LocalDateTime startDate,
//                                    LocalDateTime endDate) throws Exception {
//        return readProfileGeneric(meterSerial, meterModel, profileObis, false, startDate, endDate);
//    }

    public String readProfile_NonMD(String meterId, String meterModel, String profileObis,
                                    LocalDateTime startDate,
                                    LocalDateTime endDate) throws Exception {
        try {
            return readProfileGeneric(meterId, meterModel, profileObis, false, startDate, endDate);
        } catch (DlmsDataAccessException e) {
            // ⚠️ Handle DLMS-specific exception gracefully
            log.error("DLMS data access error for meter {}: {}", meterId, e.getMessage());
            return toJSON(Map.of(
                    "status", "error",
                    "meterId", meterId,
                    "obis", profileObis,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            // ⚠️ Catch all other runtime exceptions
            log.error("Unexpected error reading profile for meter {}: {}", meterId, e.getMessage(), e);
            return toJSON(Map.of(
                    "status", "error",
                    "meterId", meterId,
                    "obis", profileObis,
                    "message", "Unexpected error: " + e.getMessage()
            ));
        }
    }

    /**
     * Profile reading for MD/CT/PT meters (includes CT/PT ratio adjustment)
     */
    public String readProfile_MD(String meterSerial, String meterModel, String profileObis,
                                 LocalDateTime startDate,
                                 LocalDateTime endDate) throws Exception {
        try{
        return readProfileGeneric(meterSerial, meterModel, profileObis, true, startDate, endDate);
        } catch (DlmsDataAccessException e) {
            // ⚠️ Handle DLMS-specific exception gracefully
            log.error("DLMS data access error for meter {}: {}", meterSerial, e.getMessage());
            return toJSON(Map.of(
                    "status", "error",
                    "meterId", meterSerial,
                    "obis", profileObis,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            // ⚠️ Catch all other runtime exceptions
            log.error("Unexpected error reading profile for meter {}: {}", meterSerial, e.getMessage(), e);
            return toJSON(Map.of(
                    "status", "error",
                    "meterId", meterSerial,
                    "obis", profileObis,
                    "message", "Unexpected error: " + e.getMessage()
            ));
        }
    }

    // =====================================================================
    // ⚙️ CORE LOGIC
    // =====================================================================

    private String readProfileGeneric(String meterSerial, String meterModel, String profileObis, boolean useRatio,
                                      LocalDateTime startDate,
                                      LocalDateTime endDate) throws Exception {

        Map<String, Object> response = new LinkedHashMap<>();

        // 1️⃣ Validate DLMS session
        GXDLMSClient client = sessionManagerMultiVendor.getOrCreateClient(meterSerial);
        if (client == null) {
            throw new IllegalStateException("No active DLMS session. Please establish association first!");
        }

        // 2️⃣ Define meter ratios (for MD or Non-MD)
        MeterRatios meterRatios = useRatio
                ? ratioService.readMeterRatios(meterModel, meterSerial)         // If MD meter
                : new MeterRatios(1, 1, 1); // default unity ratio for Non-MD

        // 3️⃣ Define the profile object
        GXDLMSProfileGeneric profile = new GXDLMSProfileGeneric();
        profile.setLogicalName(profileObis);

        // 4️⃣ Read capture objects
        List<CaptureObjectsDTO> capturedObjects = readCaptureObjects(profile, client, meterSerial, meterModel, profileObis);
        log.info("✅ Capture {} columns read successfully:", capturedObjects.size());

        // 5️⃣ Resolve date range (default = last 2 hours)
        LocalDateTime from = (startDate != null) ? startDate : LocalDateTime.now().minusHours(2);
        LocalDateTime to = (endDate != null) ? endDate : LocalDateTime.now();

        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Start date cannot be after end date!");
        }
        GXDateTime gxFrom = new GXDateTime(Date.from(from.atZone(ZoneId.systemDefault()).toInstant()));
        GXDateTime gxTo = new GXDateTime(Date.from(to.atZone(ZoneId.systemDefault()).toInstant()));

        // 6️⃣ Read profile buffer for given time range
        log.info("🔍 Reading profile from {} to {}", from, to);
        byte[][] readRequest = client.readRowsByRange(profile, gxFrom, gxTo);
        var reply = dlmsReaderUtils.readDataBlock(client, meterSerial, readRequest[0]);
        client.updateValue(profile, 2, reply.getValue());

        // 7️⃣ Parse buffer rows
        List<Object> buffer = List.of(profile.getBuffer());
        List<ProfileRowGeneric> readings = parseProfileRows(buffer, capturedObjects, meterSerial, profileObis, meterRatios);
        log.info("✅ Parsed {} profile rows for meter={} obis={}", readings.size(), meterSerial, profileObis);

        // 8️⃣  Build JSON response
        response.put("captured size", capturedObjects.size());
        response.put("capturedObjects", capturedObjects);
        response.put("Readings size", readings.size());
        response.put("readings", readings);

        return toJSON(response);
    }


    // =====================================================================
    // 🧠 HELPER METHODS
    // =====================================================================
    private List<CaptureObjectsDTO> readCaptureObjects(GXDLMSProfileGeneric profile, GXDLMSClient client,
                                                       String meterSerial, String meterModel, String profileObis) throws Exception {

        byte[][] request = client.read(profile, 3);
        GXReplyData reply = dlmsReaderUtils.readDataBlock(client, meterSerial, request[0]);

        // 2️⃣ Check for lower-level DLMS error code
//        DlmsErrorUtils.checkError(reply, meterSerial, profileObis);

        client.updateValue(profile, 3, reply.getValue());

        List<CaptureObjectsDTO> captureList = new ArrayList<>();
        for (var entry : profile.getCaptureObjects()) {
            GXDLMSObject obj = entry.getKey();
            GXDLMSCaptureObject co = entry.getValue();

            // Read scaler and unit (already implemented)
            Map<String, Object> info = readScalerUnit(client, meterSerial, obj.getLogicalName(),
                    obj.getObjectType().getValue(), co.getAttributeIndex());

            captureList.add(CaptureObjectsDTO.builder()
                    .meterSerial(meterSerial)
                    .meterModel(meterModel)
                    .profileObis(profileObis)
                    .captureObis((String) info.get("captureObis"))
                    .classId(obj.getObjectType().getValue())
                    .attributeIndex(co.getAttributeIndex())
                    .scaler((double) info.get("scaler"))
                    .unit((String) info.get("units"))
                    .build());
        }
        return captureList;
    }

    private List<ProfileRowGeneric> parseProfileRows(List<Object> buffer, List<CaptureObjectsDTO> capturedObjects,
                                                     String meterSerial, String profileObis, MeterRatios ratios) throws JsonProcessingException {
        List<ProfileRowGeneric> readings = new ArrayList<>();

        int rowIndex = 0;
        for (Object rowObj : buffer) {
            rowIndex++;
            if (!(rowObj instanceof Object[] row)) {
                log.warn("Skipping unexpected row type at index {}: {}", rowIndex, rowObj.getClass());
                continue;
            }

            Map<String, Object> values = new LinkedHashMap<>();
            LocalDateTime timestamp = extractTimestamp(row, capturedObjects);
            if (timestamp == null) continue;
            values.put("timestamp", timestamp);

            for (int i = 1; i < row.length && i < capturedObjects.size(); i++) {
                CaptureObjectsDTO meta = capturedObjects.get(i);
                Object rawValue = row[i];
                Object decodedValue = rawValue;

                try {
                    if (rawValue instanceof byte[]) {
                        decodedValue = DlmsDataDecoder.decodeOctetString((byte[]) rawValue);
                    } else if (rawValue instanceof Number) {
                        decodedValue = applyScalingAndRatio(rawValue, meta.getUnit(), meta.getScaler(), ratios);
                    } else if (rawValue != null) {
                        decodedValue = rawValue.toString();
                    }

                    values.put(meta.getCaptureObis() + "-" + meta.getAttributeIndex(), decodedValue);

                } catch (Exception e) {
                    log.warn("⚠️ Decode failed for {}-{}: {}", meta.getCaptureObis(),
                            meta.getAttributeIndex(), e.getMessage());
                    values.put(meta.getCaptureObis() + "-" + meta.getAttributeIndex(), rawValue);
                }
            }
            readings.add(new ProfileRowGeneric(
                    Instant.now().truncatedTo(ChronoUnit.SECONDS),
                    meterSerial,
                    profileObis,
                    values
            ));
        }
        return readings;
    }

    private LocalDateTime extractTimestamp(Object[] row, List<CaptureObjectsDTO> capturedObjects) {
        if (row.length > 0 && row[0] instanceof GXDateTime gxDateTime) {
            return timestampDecoder.decodeTimestamp(gxDateTime);
        }
        return null;
    }

    private BigDecimal applyScalingAndRatio(Object rawValue, String unit, Double scaler, MeterRatios ratios) {
        if (!(rawValue instanceof Number)) return BigDecimal.ZERO;
        BigDecimal value = new BigDecimal(rawValue.toString());

        return switch (unit) {
            case "A" -> value
                    .multiply(BigDecimal.valueOf(ratios.getCtRatio()))
                    .multiply(BigDecimal.valueOf(scaler))
                    .setScale(2, RoundingMode.HALF_UP);
            case "V" -> value
                    .multiply(BigDecimal.valueOf(ratios.getPtRatio()))
                    .multiply(BigDecimal.valueOf(scaler))
                    .setScale(2, RoundingMode.HALF_UP);
            case "KW", "KVA", "KVar", "KWh", "KVAh", "KVarh" ->
                    value.multiply(BigDecimal.valueOf(ratios.getCtRatio()))
                            .multiply(BigDecimal.valueOf(scaler))
                            .divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);
            default -> value
                    .multiply(BigDecimal.valueOf(scaler))
                    .setScale(1, RoundingMode.HALF_UP);
        };
    }

    public Map<String, Object> readScalerUnit(GXDLMSClient client, String meterSerial, String captureObis,
                                              int classId, int attrIndex) throws Exception {
        double scaler = 1.0;
        String units = "";

        ObjectType type = ObjectType.forValue(classId);
        switch (type) {
            case REGISTER -> {
                GXDLMSRegister reg = new GXDLMSRegister();
                reg.setLogicalName(captureObis);
                dlmsReaderUtils.readScalerUnit(client, meterSerial, reg, 3);
                scaler = (reg.getScaler() == 0) ? 1.0 : reg.getScaler();
                units = getUnitSymbol(reg.getUnit());
            }
            case DEMAND_REGISTER -> {
                GXDLMSDemandRegister dr = new GXDLMSDemandRegister();
                dr.setLogicalName(captureObis);
                dlmsReaderUtils.readScalerUnit(client, meterSerial, dr, 4);
                scaler = (dr.getScaler() == 0) ? 1.0 : dr.getScaler();
                units = getUnitSymbol(dr.getUnit());
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("captureObis", captureObis);
        response.put("scaler", scaler);
        response.put("units", units);
        return response;
    }

    public String toJSON(Object data) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
        return mapper.writeValueAsString(data);
    }

    private Object decodeOctetString1(Object rawValue, CaptureObjectsDTO meta) {
        try {
            byte[] bytes = (rawValue instanceof byte[]) ? (byte[]) rawValue
                    : GXCommon.hexToBytes(String.valueOf(rawValue));

            // Detect DLMS datetime pattern
            if (bytes.length >= 12) {
                LocalDateTime dt = timestampDecoder.decodeTimestamp(bytes);
                if (dt != null) return dt;
            }

            // Check if ASCII
            String ascii = new String(bytes, StandardCharsets.US_ASCII);
            if (ascii.matches("\\d+")) return new BigDecimal(ascii);
            if (ascii.chars().allMatch(Character::isLetterOrDigit)) return ascii;

            // Default: hex representation
            return GXCommon.toHex(bytes);
        } catch (Exception e) {
            log.warn("⚠️ OctetString decode failed for OBIS {}: {}", meta.getCaptureObis(), e.getMessage());
            assert rawValue instanceof byte[];
            return GXCommon.toHex((byte[]) rawValue);
        }
    }

    private Object decodeOctetString(Object rawValue) {
        try {
            byte[] bytes = (rawValue instanceof byte[])
                    ? (byte[]) rawValue
                    : GXCommon.hexToBytes(String.valueOf(rawValue));

            // --- STEP 1️⃣ Try DLMS DateTime decoding (if bytes look like a timestamp) ---
            if (bytes.length >= 12) {
                LocalDateTime dt = timestampDecoder.decodeTimestamp(bytes);
                if (dt != null) {
                    return dt;
                }
            }

            // --- STEP 2️⃣ Try printable ASCII interpretation ---
            String ascii = new String(bytes, StandardCharsets.US_ASCII);

            // Ensure all bytes are within printable ASCII range
            boolean printable = ascii.chars().allMatch(c -> c >= 32 && c <= 126);
            if (printable) {
                // Always treat printable bytes as String (serials, tokens, IDs, etc.)
                return ascii.trim();
            }

            // --- STEP 3️⃣ Default fallback to HEX representation ---
            return GXCommon.toHex(bytes);

        } catch (Exception e) {
            log.warn("⚠️ OctetString decode failed for OBIS : {}",e.getMessage());
            return (rawValue instanceof byte[])
                    ? GXCommon.toHex((byte[]) rawValue)
                    : String.valueOf(rawValue);
        }
    }
    private boolean isLikelyDlmsDateTime(byte[] bytes) {
        if (bytes == null || bytes.length < 12) return false;

        // DLMS DateTime format always starts with 0x07
        if (bytes[0] != 0x07) return false;

        int year = ((bytes[1] & 0xFF) << 8) | (bytes[2] & 0xFF);
        int month = bytes[3] & 0xFF;
        int day = bytes[4] & 0xFF;
        int hour = bytes[5] & 0xFF;
        int minute = bytes[6] & 0xFF;
        int second = bytes[7] & 0xFF;

        // Validate logical ranges
        return (year >= 1990 && year <= 2100)
                && (month >= 1 && month <= 12)
                && (day >= 1 && day <= 31)
                && (hour <= 23)
                && (minute <= 59)
                && (second <= 59);
    }
}
