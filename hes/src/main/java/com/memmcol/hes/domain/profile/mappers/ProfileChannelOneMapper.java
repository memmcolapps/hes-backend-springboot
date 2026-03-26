package com.memmcol.hes.domain.profile.mappers;

import com.memmcol.hes.application.port.out.GenericDtoMappers;
import com.memmcol.hes.domain.profile.MeterRatios;
import com.memmcol.hes.domain.profile.ObisObjectType;
import com.memmcol.hes.domain.profile.ProfileMetadataResult;
import com.memmcol.hes.domain.profile.ProfileRowGeneric;
import com.memmcol.hes.dto.ProfileChannelOneDTO;
import com.memmcol.hes.infrastructure.dlms.DlmsTimestampDecoder;
import com.memmcol.hes.service.MeterRatioService;
import gurux.dlms.GXDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class ProfileChannelOneMapper implements GenericDtoMappers<ProfileChannelOneDTO> {
    private final MeterRatioService ratioService;
    private final DlmsTimestampDecoder dlmsTimestampDecoder;
//    private final DlmsTimestampDecoder timestampDecoder;

    @Override
    public List<ProfileChannelOneDTO> toDTO(List<ProfileRowGeneric> rawRows, String meterSerial, String modelNumber, boolean mdMeter, ProfileMetadataResult metadataResult) throws Exception {
        // Pre-fetch meter ratios if MD
        MeterRatios meterRatios = mdMeter ? ratioService.readMeterRatios(modelNumber, meterSerial) : null;

        return rawRows.stream()
                .map(raw -> mapRow(raw, meterSerial, modelNumber, mdMeter, metadataResult, meterRatios))
                .collect(Collectors.toList());
    }

    @Override
    public ProfileChannelOneDTO mapRow(ProfileRowGeneric raw, String meterSerial, String modelNumber, boolean mdMeter, ProfileMetadataResult metadataResult, MeterRatios meterRatios) {
        ProfileChannelOneDTO dto = new ProfileChannelOneDTO();
        dto.setMeterSerial(meterSerial);
        dto.setModelNumber(modelNumber);

        // Iterate over all OBIS values
        for (Map.Entry<String, Object> entry : raw.getValues().entrySet()) {
            String obisWithAttr = entry.getKey();                     // e.g. 1.0.129.6.0.255-2
            String obisCode = obisWithAttr.split("-")[0];         // e.g. 1.0.129.6.0.255
            Object rawValue = entry.getValue();

            /*
            *
            * String obisWithAttr = entry.getKey();                     // e.g. 1.0.129.6.0.255-2
            String baseObis     = obisWithAttr.split("-")[0];         // e.g. 1.0.129.6.0.255
            Object rawValue     = entry.getValue();
*/

            if (rawValue == null) continue;

            // Get persistence info (scaler, multiplyBy)
            ProfileMetadataResult.ProfilePersistenceInfo persistenceInfo = metadataResult.forPersistence(obisCode);
            if (persistenceInfo == null) continue;

            double scaler = persistenceInfo.getScaler();
            String multiplyBy = persistenceInfo.getMultiplyBy();
            ObisObjectType objectType = persistenceInfo.getType();  //1️⃣ Identify object type from metadata. Possible: CLOCK, NON_SCALER, SCALER

            // Set entry timestamp from CLOCK OBIS
            if (objectType == ObisObjectType.CLOCK) {
                LocalDateTime tsInstant;
                switch (rawValue) {
                    case LocalDateTime localDateTime ->
                        // Already a LocalDateTime
                            tsInstant = localDateTime;
                    case String s ->
                        // ISO-8601 date string (e.g., 2025-08-01T00:00)
                            tsInstant = LocalDateTime.parse(s);
                    case byte[] bytes ->
                        // Raw bytes → decode to LocalDateTime
                            tsInstant = dlmsTimestampDecoder.decode(bytes);
                    default -> {
                        // Fallback: unexpected type
                        log.warn("Unexpected timestamp type: {} for OBIS={}, using fallback",
                                rawValue.getClass().getName(), obisCode);
                        tsInstant = LocalDateTime.ofInstant(raw.getTimestamp(), ZoneId.systemDefault());
                    }
                }
                dto.setEntryTimestamp(tsInstant.truncatedTo(ChronoUnit.SECONDS));
                continue;
            }

            BigDecimal finalValue;
            try {
                BigDecimal value = new BigDecimal(rawValue.toString());

                // 2️⃣ If NON_SCALER → keep value as-is
                if (objectType == ObisObjectType.NON_SCALER) {
                    finalValue = value;

                    // 3️⃣ If SCALER → apply scaler and CT/PT logic
                } else {
                    if (mdMeter) {
                        switch (multiplyBy.toUpperCase()) {
                            case "CTPT" -> finalValue = value.multiply(BigDecimal.valueOf(scaler))
                                    .multiply(BigDecimal.valueOf(meterRatios.getCtptRatio()));
                            case "CT" -> finalValue = value.multiply(BigDecimal.valueOf(scaler))
                                    .multiply(BigDecimal.valueOf(meterRatios.getCtRatio()));
                            case "PT" -> finalValue = value.multiply(BigDecimal.valueOf(scaler))
                                    .multiply(BigDecimal.valueOf(meterRatios.getPtRatio()));
                            default -> finalValue = value.multiply(BigDecimal.valueOf(scaler));
                        }
                    } else {
                        finalValue = value.multiply(BigDecimal.valueOf(scaler));
                    }
                }

                // 4️⃣ Round final value
                finalValue = finalValue.setScale(2, RoundingMode.HALF_UP);

                // 5️⃣ Map to DTO
                ProfileMetadataResult.ProfileMappingInfo mappingInfo = metadataResult.forMapping(obisCode);
                if (mappingInfo != null) {
                    setDtoField(dto, mappingInfo.getColumnName(), finalValue);
                }
            } catch (NumberFormatException e) {
                log.error("Obis code: {}, multiplyBy: {}, objectType: {}, NumberFormatException {}", obisCode, multiplyBy, objectType, e.getMessage());
            }
        }
        dto.setReceivedAt(LocalDateTime.now());
        return dto;
    }

    @Override
    public void setDtoField(ProfileChannelOneDTO dto, String columnName, BigDecimal value) {
        log.info("column mapping: {}, value: {}", columnName, value);
        switch (columnName.toLowerCase()) {
            case "entry_timestamp" -> dto.setEntryTimestamp(asDateTime(value));
            case "profile_status" -> dto.setMeterHealthIndicator(value.intValue());
            case "instantaneous_voltage_l1" -> dto.setInstantaneousVoltageL1(value.doubleValue());
            case "instantaneous_voltage_l2" -> dto.setInstantaneousVoltageL2(value.doubleValue());
            case "instantaneous_voltage_l3" -> dto.setInstantaneousVoltageL3(value.doubleValue());
            case "instantaneous_current_l1" -> dto.setInstantaneousCurrentL1(value.doubleValue());
            case "instantaneous_current_l2" -> dto.setInstantaneousCurrentL2(value.doubleValue());
            case "instantaneous_current_l3" -> dto.setInstantaneousCurrentL3(value.doubleValue());
            case "instantaneous_active_power" -> dto.setInstantaneousActivePower(value.doubleValue());
            case "instantaneous_reactive_import" -> dto.setInstantaneousReactiveImport(value.doubleValue());
            case "instantaneous_reactive_export" -> dto.setInstantaneousReactiveExport(value.doubleValue());
            case "instantaneous_power_factor" -> dto.setInstantaneousPowerFactor(value.doubleValue());
            case "instantaneous_apparent_power" -> dto.setInstantaneousApparentPower(value.doubleValue());
            case "instantaneous_net_frequency" -> dto.setInstantaneousNetFrequency(value.doubleValue());
            default -> log.warn("Unknown column mapping: {}", columnName);
        }

    }

    private LocalDateTime asDateTime(Object value) {

        if (value == null) return null;

        // 1. Gurux DLMS type
        if (value instanceof GXDateTime gx) {
            // Preferred if available
            try {
                return toLocalDateTime(gx);
            } catch (Exception e) {
                // fallback if method not supported
                return gx.getValue().toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();
            }
        }

        // 2. java.util.Date
        if (value instanceof Date d) {
            return d.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
        }

        // 3. Already LocalDateTime
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }

        // 4. String fallback (last resort)
        if (value instanceof String s) {
            return LocalDateTime.parse(s);
        }

        throw new IllegalArgumentException(
                "Unsupported DateTime type: " + value.getClass()
        );
    }

    public static LocalDateTime toLocalDateTime(GXDateTime gx) {
        if (gx == null || gx.getValue() == null) {
            return null;
        }

        Date date = gx.getValue();

        return gx.getValue().toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }

}
