package com.memmcol.hes.domain.profile.mappers;

import com.memmcol.hes.application.port.out.GenericDtoMappers;
import com.memmcol.hes.domain.profile.MeterRatios;
import com.memmcol.hes.domain.profile.ObisObjectType;
import com.memmcol.hes.domain.profile.ProfileMetadataResult;
import com.memmcol.hes.domain.profile.ProfileRowGeneric;
import com.memmcol.hes.dto.ProfileChannelOneDTO;
import com.memmcol.hes.dto.ProfileChannelTwoDTO;
import com.memmcol.hes.infrastructure.dlms.DlmsTimestampDecoder;
import com.memmcol.hes.service.MeterRatioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class ProfileChannelTwoMapper implements GenericDtoMappers<ProfileChannelTwoDTO> {
    private final MeterRatioService ratioService;
    private final DlmsTimestampDecoder dlmsTimestampDecoder;

    @Override
    public List<ProfileChannelTwoDTO> toDTO(List<ProfileRowGeneric> rawRows, String meterSerial, String modelNumber, boolean mdMeter, ProfileMetadataResult captureObjects) throws Exception {
        // Pre-fetch meter ratios if MD
        MeterRatios meterRatios = mdMeter ? ratioService.readMeterRatios(modelNumber, meterSerial) : null;

        return rawRows.stream()
                .map(raw -> mapRow(raw, meterSerial, modelNumber, mdMeter, captureObjects, meterRatios))
                .collect(Collectors.toList());
    }

    @Override
    public ProfileChannelTwoDTO mapRow(ProfileRowGeneric raw, String meterSerial, String modelNumber, boolean mdMeter, ProfileMetadataResult captureObjects, MeterRatios meterRatios) {
        ProfileChannelTwoDTO dto = new ProfileChannelTwoDTO();
        dto.setMeterSerial(meterSerial);
        dto.setModelNumber(modelNumber);

        // Iterate over all OBIS values
        for (Map.Entry<String, Object> entry : raw.getValues().entrySet()) {
            String obisWithAttr = entry.getKey();                     // e.g. 1.0.129.6.0.255-2
            String obisCode = obisWithAttr.split("-")[0];         // e.g. 1.0.129.6.0.255
            Object rawValue = entry.getValue();

            if (rawValue == null) continue;

            // Get persistence info (scaler, multiplyBy)
            ProfileMetadataResult.ProfilePersistenceInfo persistenceInfo = captureObjects.forPersistence(obisCode);
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
                ProfileMetadataResult.ProfileMappingInfo mappingInfo = captureObjects.forMapping(obisCode);
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
    public void setDtoField(ProfileChannelTwoDTO dto, String columnName, BigDecimal value) {
        switch (columnName.toLowerCase()) {
            case "total_import_active_energy" -> dto.setTotalImportActiveEnergy(value.doubleValue());
            case "total_export_active_energy" -> dto.setTotalExportActiveEnergy(value.doubleValue());
            default -> log.warn("Unknown column mapping: {}", columnName);
        }
    }
}
