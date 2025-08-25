package com.memmcol.hes.domain.profile.mappers;

import com.memmcol.hes.application.port.out.GenericDtoMappers;
import com.memmcol.hes.domain.profile.MeterRatios;
import com.memmcol.hes.domain.profile.ObisObjectType;
import com.memmcol.hes.domain.profile.ProfileMetadataResult;
import com.memmcol.hes.domain.profile.ProfileRowGeneric;
import com.memmcol.hes.dto.ProfileChannelOneDTO;
import com.memmcol.hes.infrastructure.dlms.DlmsTimestampDecoder;
import com.memmcol.hes.service.MeterRatioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
            String obisCode = entry.getKey();
            Object rawValue = entry.getValue();

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
                dto.setEntryTimestamp(tsInstant);
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
        switch (columnName.toLowerCase()) {
            case "meter_health_indicator" -> dto.setMeterHealthIndicator(value.intValue());
            case "total_instantaneous_active_power" -> dto.setTotalInstantaneousActivePower(value.doubleValue());
            case "total_instantaneous_apparent_power" -> dto.setTotalInstantaneousApparentPower(value.doubleValue());
            case "l1_current_harmonic_thd" -> dto.setL1CurrentHarmonicThd(value.doubleValue());
            case "l2_current_harmonic_thd" -> dto.setL2CurrentHarmonicThd(value.doubleValue());
            case "l3_current_harmonic_thd" -> dto.setL3CurrentHarmonicThd(value.doubleValue());
            case "l1_voltage_harmonic_thd" -> dto.setL1VoltageHarmonicThd(value.doubleValue());
            case "l2_voltage_harmonic_thd" -> dto.setL2VoltageHarmonicThd(value.doubleValue());
            case "l3_voltage_harmonic_thd" -> dto.setL3VoltageHarmonicThd(value.doubleValue());
            default -> log.warn("Unknown column mapping: {}", columnName);
        }

    }


}
