package com.memmcol.hesTraining.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.memmcol.hes.application.port.out.TxRxService;
import com.memmcol.hes.domain.profile.ProfileRowGeneric;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.infrastructure.dlms.DlmsTimestampDecoder;
import com.memmcol.hes.nettyUtils.SessionManagerMultiVendor;
import com.memmcol.hes.service.MeterRatioService;
import com.memmcol.hesTraining.dto.CaptureObjectsDTO;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXDateTime;
import gurux.dlms.GXReplyData;
import gurux.dlms.enums.ObjectType;
import gurux.dlms.enums.Unit;
import gurux.dlms.objects.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.memmcol.hesTraining.services.MeterReadingService.getUnitSymbol;

@Service
@Slf4j
@AllArgsConstructor
public class ProfileReadingService {
    private final SessionManagerMultiVendor sessionManagerMultiVendor;
    private final DlmsReaderUtils dlmsReaderUtils;
    private final DlmsTimestampDecoder timestampDecoder;

    /*TODO:
     *  1. Create client or get existing client
     *  2. Load profile OBIS code
     *  3. Read capture objects
     *  4. Read scaler of each captured objects
     *  5. Read by date range
     *  6. Multiply result by scaler
     *  7. Return initial values.
     *  8. If MD meter, read CTPT Ratio
     *  9. Apply CTPT ratio
     *  10. Return JSON object.
     *  11. Notes: Use Gurux for item 1 to 4
     *  12. Use my new method for item 5
     * */

    public String readProfile_NonMD(String meterSerial, String meterModel, String profileObis) throws Exception {
        List<CaptureObjectsDTO> capturedObjects = new ArrayList<>();
        double scaler = 1.0;
        Unit DLMSUnits;
        String unit = "";

        //Establish DLMS Association
        GXDLMSClient client = sessionManagerMultiVendor.getOrCreateClient(meterSerial);
        if (client == null) {
            throw new IllegalStateException("No active session, Please establish association before reading OBIS!");
        }

        //Load profile OBIS code
        GXDLMSProfileGeneric profile = new GXDLMSProfileGeneric();
        profile.setLogicalName(profileObis);

        // Read attribute 3 (CaptureObjects)
        byte[][] request = client.read(profile, 3);
        GXReplyData reply = dlmsReaderUtils.readDataBlock(client, meterSerial, request[0]);
        client.updateValue(profile, 3, reply.getValue());

        // For each captured object, read scaler and units
        List<Map.Entry<GXDLMSObject, GXDLMSCaptureObject>> captureObjects = profile.getCaptureObjects();
        for (Map.Entry<GXDLMSObject, GXDLMSCaptureObject> entry : captureObjects) {
            GXDLMSObject obj = entry.getKey();
            GXDLMSCaptureObject co = entry.getValue();

            String captureObis = obj.getLogicalName();
            int classId = obj.getObjectType().getValue();
            int attrIndex = co.getAttributeIndex();

            // Read Scaler+Unit first
            Map<String, Object> captureObjectMap = readScalerUnit(client, meterSerial, captureObis, classId, attrIndex);
            scaler = (double) captureObjectMap.get("scaler");
            unit = (String) captureObjectMap.get("units");

            //Build the objects
            CaptureObjectsDTO objectsDTO = CaptureObjectsDTO.builder()
                    .meterSerial(meterSerial)
                    .meterModel(meterModel)
                    .profileObis(profileObis)
                    .captureObis(captureObis)
                    .classId(classId)
                    .attributeIndex(attrIndex)
                    .scaler(scaler)
                    .unit(unit)
                    .build();

            capturedObjects.add(objectsDTO);
        }
        log.info("capture columns read successfully: {}", returnJSON(capturedObjects));

        //5. Read by date range
        //Read last 2 hours for load profile.
        LocalDateTime from = LocalDateTime.now().minusHours(2);
        LocalDateTime to = LocalDateTime.now();
        GXDateTime gxFrom = new GXDateTime(Date.from(from.atZone(ZoneId.systemDefault()).toInstant()));
        GXDateTime gxTo = new GXDateTime(Date.from(to.atZone(ZoneId.systemDefault()).toInstant()));
        log.info("Reading profile {} from {} to {}", profileObis, from, to);

        // 📨 Read rows from the meter using date range
        byte[][] readRequest = client.readRowsByRange(profile, gxFrom, gxTo); // null = up to latest
        GXReplyData replyData = dlmsReaderUtils.readDataBlock(client, meterSerial, readRequest[0]);
        client.updateValue(profile, 2, replyData.getValue());

        //Search for this ->  //Next profile by date range (Using Gurux)
        List<ProfileRowGeneric> readings = new ArrayList<>();
        List<Object> buffer = List.of(profile.getBuffer());

        // Iterate through each profile row
        int rowIndex = 0;
        Map<String, Object> values = new LinkedHashMap<>();
        BigDecimal finalValue = BigDecimal.ONE;
        for (Object rowObj : buffer) {
            rowIndex++;

            if (!(rowObj instanceof Object[] row)) {
                log.warn("Skipping unexpected row type at index {}: {}", rowIndex, rowObj.getClass());
                continue;
            }

            // Ensure timestamp exists
            LocalDateTime timestamp = null;
            if (row.length > 0 && row[0] instanceof GXDateTime gxDateTime) {
                CaptureObjectsDTO meta = capturedObjects.get(0);
                String key = meta.getCaptureObis();
                timestamp = timestampDecoder.decodeTimestamp(gxDateTime);
                values.put(key, timestamp);
            }
            if (timestamp == null) {
                log.debug("Skipping row {} (no timestamp) meter={} obis={}", rowIndex, meterSerial, profileObis);
                continue;
            }

            // Map all other columns (except timestamp)
             for (int i = 1; i < row.length && i < capturedObjects.size(); i++) {
                CaptureObjectsDTO meta = capturedObjects.get(i);
                String key = meta.getCaptureObis() + "-" + meta.getAttributeIndex();
                Object rawValue = row[i];

                // Apply scaling (if numeric)
                 if (rawValue instanceof Number) {
//                    scaledValue = ((Number) rawValue).doubleValue() * meta.getScaler();
                    switch (meta.getUnit()) {
                        case "A" ->  // current-related
                        {
                            finalValue = new BigDecimal(rawValue.toString())
                                    .setScale(2, RoundingMode.HALF_UP);
                        }

                        case "V" ->  // voltage-related
                                finalValue = new BigDecimal(rawValue.toString())
                                        .setScale(2, RoundingMode.HALF_UP);

                        case "KW", "KVA", "KVar", "KWh", "KVAh", "KVarh" -> // power/energy-related
                                finalValue = new BigDecimal(rawValue.toString())
                                        .divide(new BigDecimal(1000), 2, RoundingMode.HALF_UP);  //divide by 1,000 to convert to Kilo.....
//                                            .setScale(2, RoundingMode.HALF_UP);

                        default -> // fallback (no scaling)
                                finalValue = new BigDecimal(rawValue.toString())
                                        .setScale(2, RoundingMode.HALF_UP);
                    }
                }

                values.put(key, finalValue);
            }

            ProfileRowGeneric profileRow = new ProfileRowGeneric(
                    Instant.now().truncatedTo(ChronoUnit.SECONDS),
                    meterSerial,
                    profileObis,
                    values
            );

            readings.add(profileRow);
        }

        log.info("✅ Parsed {} profile rows for meter={} obis={}", readings.size(), meterSerial, profileObis);

        // Optional: persist or return readings
        // ✅ Convert readings to JSON for debug/inspection
        String json = "";
        try {
            ObjectMapper mapper = new ObjectMapper()
                    .registerModule(new JavaTimeModule())                          // ✅ Enables Java 8 date/time
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)       // ✅ Use ISO-8601, not numeric timestamps
                    .enable(SerializationFeature.INDENT_OUTPUT);
            json = mapper.writeValueAsString(readings);
            log.info("📊 DLMS Profile Data ({} rows).", readings.size());
        } catch (Exception e) {
            log.error("❌ Failed to serialize profile readings to JSON", e);
        }
        return json;
    }

    public Map<String, Object> readScalerUnit(GXDLMSClient client, String meterSerial, String captureObis, int classId, int attrIndex) throws Exception {
        double scaler = 1.0;
        Unit dlsmUnit;
        Object result;
        String units = "";

        if (client == null || classId == 0 || captureObis.isEmpty() || attrIndex == 0 || meterSerial.isEmpty()) {
            throw new IllegalArgumentException("All parameters are required");
        }

        ObjectType type = ObjectType.forValue(classId);
        switch (type) {
            case REGISTER -> {
                GXDLMSRegister reg = new GXDLMSRegister();
                reg.setLogicalName(captureObis);

                // Read Scaler+Unit first
                dlmsReaderUtils.readScalerUnit(client, meterSerial, reg, 3);
                scaler = reg.getScaler();
                if (scaler == 0) {
                    scaler = 1.0;
                }
                dlsmUnit = reg.getUnit();
                units = getUnitSymbol(dlsmUnit);
            }
            case DEMAND_REGISTER -> {
                GXDLMSDemandRegister dr = new GXDLMSDemandRegister();
                dr.setLogicalName(captureObis);

                // Read Scaler+Unit
                dlmsReaderUtils.readScalerUnit(client, meterSerial, dr, 4);
                scaler = dr.getScaler();
                if (scaler == 0) {
                    scaler = 1.0;
                }
                dlsmUnit = dr.getUnit();
                units = getUnitSymbol(dlsmUnit);
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("captureObis", captureObis);
        response.put("scaler", scaler);
        response.put("units", units);

        return response;
    }

    public String returnJSON(Object data) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())                          // ✅ Enables Java 8 date/time
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)       // ✅ Use ISO-8601, not numeric timestamps
                .enable(SerializationFeature.INDENT_OUTPUT);
        return mapper.writeValueAsString(data);
    }
}
