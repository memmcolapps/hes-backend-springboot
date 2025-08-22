package com.memmcol.hes.domain.profile;

import com.memmcol.hes.application.port.out.MeterLockPort;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.nettyUtils.SessionManager;
import com.memmcol.hes.service.MeterRatioService;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXDateTime;
import gurux.dlms.enums.ObjectType;
import gurux.dlms.internal.GXCommon;
import gurux.dlms.objects.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@RequiredArgsConstructor
@Service
@Slf4j
public class InstantaneousReadService {

    private final MeterLockPort meterLockPort;
    private final DlmsReaderUtils dlmsReaderUtils;
    private final ObisMappingService obisMappingService;
    private final MeterRatioService ratioService;

    public Map<String, Object> readVoltagesAndCurrents(String meterSerial, String model) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("Numerator of CT ratio", readObisValue(model, meterSerial, "1;1.0.0.4.2.255;2;0", true)); // V L1
        result.put("Denominator of CT ratio", readObisValue(model, meterSerial, "1;1.0.0.4.5.255;2;0", true)); // V L1
        result.put("Numerator of PT ratio", readObisValue(model, meterSerial, "1;1.0.0.4.3.255;2;0", true)); // V L1
        result.put("Denominator of PT ratio", readObisValue(model, meterSerial, "1;1.0.0.4.6.255;2;0", true)); // V L1
        result.put("Undefine ratio", readObisValue(model, meterSerial, "1;1.0.0.4.4.255;2;0", true)); // V L1

        // Example OBIS codes for voltage & current — replace with real ones
        result.put("voltageL1", readObisValue(model, meterSerial, "3;1.0.32.7.0.255;2;0", true)); // V L1
        result.put("voltageL2", readObisValue(model, meterSerial, "3;1.0.52.7.0.255;2;0", true)); // V L2
        result.put("voltageL3", readObisValue(model, meterSerial, "3;1.0.72.7.0.255;2;0", true)); // V L3

        result.put("currentL1", readObisValue(model, meterSerial, "3;1.0.31.7.0.255;2;0", true)); // I L1
        result.put("currentL2", readObisValue(model, meterSerial, "3;1.0.51.7.0.255;2;0", true)); // I L2
        result.put("currentL3", readObisValue(model, meterSerial, "3;1.0.71.7.0.255;2;0", true)); // I L3

        return result;

    }

    public Map<String, String> readObisValue(String model, String meterSerial, String obisCombined, boolean isMDMeter) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        result.put(obisCombined, "Unknown error");  //Initial value

        //Step 1: Decode the obisCombined and extract its values
        String[] parts = obisCombined.split(";");
        if (parts.length != 4) {
            throw new IllegalArgumentException("OBIS format must be: classId;obisCode;attributeIndex;dataIndex");
        }
        int classId = Integer.parseInt(parts[0]);
        String obisCode = parts[1].trim();
        int attributeIndex = Integer.parseInt(parts[2]);
        int dataIndex = Integer.parseInt(parts[3]);

        //Step 2: Read from meter
        Object rawValue = dlmsReaderUtils.readObisObject(meterSerial, obisCode, classId, attributeIndex);

        //Step 3 : Process data
        ObjectType type = ObjectType.forValue(classId);
        switch (type) {
            case REGISTER -> {
                result = scaleValues(model, meterSerial, obisCombined, rawValue, isMDMeter);
            }
            case EXTENDED_REGISTER -> {
                if (attributeIndex == 2) {  //NUmber
                    result = scaleValues(model, meterSerial, obisCombined, rawValue, isMDMeter);
                }
                if (attributeIndex == 5) {  //DateTime
                    result = detailedValues(model, obisCombined, extractDateTime(rawValue));
                }
            }
            case CLOCK -> {
                result = detailedValues(model, obisCombined, extractDateTime(rawValue));
            }
            default -> {
                result = detailedValues(model, obisCombined, rawValue);
            }
        }
        return result.isEmpty() ? null : result;
    }


    public String extractDateTime(Object raw) {
        GXDateTime clockDateTime;
        if (raw instanceof GXDateTime dt) {
            clockDateTime = dt;
        } else if (raw instanceof byte[] array) {
            clockDateTime = GXCommon.getDateTime(array);
        } else {
            throw new IllegalArgumentException("❌ Unexpected clock result type: " + raw.getClass());
        }
        // Convert to LocalDateTime
        LocalDateTime localDateTime = clockDateTime.getMeterCalendar().toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        // Format to "YYYY-MM-DD HH:MM:SS"
        return localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public Map<String, String> detailedValues(String model, String obisCombined, Object rawValue) throws Exception {
        if (rawValue == null) {
            throw new IllegalStateException("No value found for OBIS code: " + obisCombined);
        }
        Map<String, String> resultMap = new LinkedHashMap<>();
        String description = "Unknown";
        Map<String, String> descriptionMap = obisMappingService.getDescriptionForModel(model);
        ;
        if (descriptionMap.containsKey(obisCombined)) {
            description = descriptionMap.get(obisCombined);
        }
        resultMap.put(description, rawValue.toString());
        return resultMap;
    }

    public Map<String, String> scaleValues(String model, String meterSerial, String obisCombined, Object rawValue, boolean isMDMeter) throws Exception {
        if (rawValue == null) {
            throw new IllegalStateException("No value found for OBIS code: " + obisCombined);
        }
        Map<String, String> resultMap = new LinkedHashMap<>();
        BigDecimal value = new BigDecimal(rawValue.toString());

        //Step 1:  Read scaler
        Double scaler = 1.00;
        String usesCtpt = "CT";  //Default for MD meters. For non-MD is it NONE
        String description = "Unknown";
        BigDecimal finalValue = BigDecimal.ONE;
        Map<String, MultiplierDTO> multiplier = obisMappingService.getScalerAndPurposeMap(model);
        if (multiplier.containsKey(obisCombined)) {
            MultiplierDTO data = multiplier.get(obisCombined);
            scaler = data.getScaler();
            usesCtpt = data.getPurpose();
            description = data.getDescription();
            log.debug("rawValue: {}, Scaler: {}, Use CTPT?: {}", rawValue, scaler, usesCtpt);
        }

        if (isMDMeter) {
            //Step 2:  Read CTPT
            MeterRatios meterRatios = ratioService.readMeterRatios(model, meterSerial);
            log.info("MeterRatios: {}", meterRatios);

            switch (usesCtpt.toUpperCase()) {
                case "CTPT" -> {
                    finalValue = value
                            .multiply(BigDecimal.valueOf(scaler))
                            .multiply(BigDecimal.valueOf(meterRatios.getCtptRatio()))
                            .setScale(2, RoundingMode.HALF_UP);
                }
                case "CT" -> {
                    finalValue = value
                            .multiply(BigDecimal.valueOf(scaler))
                            .multiply(BigDecimal.valueOf(meterRatios.getCtRatio()))
                            .setScale(2, RoundingMode.HALF_UP);
                }
                case "PT" -> {
                    finalValue = value
                            .multiply(BigDecimal.valueOf(scaler))
                            .multiply(BigDecimal.valueOf(meterRatios.getPtRatio()))
                            .setScale(2, RoundingMode.HALF_UP);
                }
                default -> {
                    finalValue = value
                            .multiply(BigDecimal.valueOf(scaler))
                            .setScale(2, RoundingMode.HALF_UP);
                }
            }
            ;

        } else { //Non-MD
            finalValue = value
                    .multiply(BigDecimal.valueOf(scaler))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        String formatted = new DecimalFormat("#,##0.00").format(finalValue);
        resultMap.put(description, formatted);
        return resultMap;
    }

    public Object readInstantaneousValues(String model, String meterSerial, String obisCombined) throws Exception {
        //Step 1:  Read raw value from meter
        Object rawValue = readObisValue(model, meterSerial, obisCombined, true);
        Object unwrapped = unwrapGuruxValue(rawValue);
        if (unwrapped == null) {
            throw new IllegalStateException("No value found for OBIS code: " + obisCombined);
        }

        // Step 2: Handle numeric values
        if (unwrapped instanceof Integer || unwrapped instanceof BigDecimal) {
            //Step 3:  Read CTPT
            MeterRatios meterRatios = ratioService.readMeterRatios(model, meterSerial);
            log.info("MeterRatios: {}", meterRatios);

            //Step 4:  Read scaler and CTPT multiplier
            /**
             * Get "usesCtpt" from DB using meterSerial
             * If non-MD meter, usesCtpt = NONE
             * If MD meter, it will check the OBIS mapping table from the "purpose" column value
             */
            Double scaler = 1.00;
            String usesCtpt = "CT";  //Default for MD meters. For non-MD is it NONE
            Map<String, MultiplierDTO> multiplier = obisMappingService.getScalerAndPurposeMap("model");
            if (multiplier.containsKey(obisCombined)) {
                MultiplierDTO data = multiplier.get(obisCombined);
                scaler = data.getScaler();
                usesCtpt = data.getPurpose();
                log.debug("Scaler: {}, Use CTPT?: {}", scaler, usesCtpt);
            }

            assert unwrapped instanceof BigDecimal;
            BigDecimal value = (BigDecimal) unwrapped;
            return switch (usesCtpt.toUpperCase()) {
                case "CTPT" -> {
                    BigDecimal finalValue = value
                            .multiply(BigDecimal.valueOf(scaler))
                            .multiply(BigDecimal.valueOf(meterRatios.getCtptRatio()))
                            .setScale(2, RoundingMode.HALF_UP);
                    yield finalValue.doubleValue();
                }
                case "CT" -> {
                    BigDecimal finalValue = value
                            .multiply(BigDecimal.valueOf(scaler))
                            .multiply(BigDecimal.valueOf(meterRatios.getCtRatio()))
                            .setScale(2, RoundingMode.HALF_UP);
                    yield finalValue.doubleValue();
                }
                case "PT" -> {
                    BigDecimal finalValue = value
                            .multiply(BigDecimal.valueOf(scaler))
                            .multiply(BigDecimal.valueOf(meterRatios.getPtRatio()))
                            .setScale(2, RoundingMode.HALF_UP);
                    yield finalValue.doubleValue();
                }
                default -> {
                    BigDecimal finalValue = value
                            .multiply(BigDecimal.valueOf(scaler))
                            .setScale(2, RoundingMode.HALF_UP);
                    yield finalValue.doubleValue();
                }
            };
        }

        // Step 5: If String or DateTime, return as-is
        return unwrapped;
    }

    public Object unwrapGuruxValue(Object rawValue) {
        if (rawValue == null) return null;

        // Handle known Gurux types
        if (rawValue instanceof IGXDLMSBase) {
            try {
                // Most Gurux types override toString or have getValue()
                Method getValueMethod = rawValue.getClass().getMethod("getValue");
                return getValueMethod.invoke(rawValue);
            } catch (Exception e) {
                log.warn("Could not unwrap Gurux type: {}", rawValue.getClass().getName());
            }
        }

        // You can also handle GXDateTime here, if needed
        if (rawValue instanceof GXDateTime) {
            return ((GXDateTime) rawValue).getValue(); // or format it
        }

        return rawValue;
    }
}
