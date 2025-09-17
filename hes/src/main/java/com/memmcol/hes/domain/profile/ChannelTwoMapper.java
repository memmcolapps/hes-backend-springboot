package com.memmcol.hes.domain.profile;

import com.memmcol.hes.infrastructure.dlms.DlmsTimestampDecoder;
import com.memmcol.hes.model.ProfileChannel2Reading;
import com.memmcol.hes.model.ProfileChannel2ReadingDTO;
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
public class ChannelTwoMapper {

    private final MeterRatioService ratioService;
    private final DlmsTimestampDecoder timestampDecoder;

    public List<ProfileChannel2ReadingDTO> toDTO(List<ProfileRowGeneric> rawRows,
                                              String meterSerial,
                                              String modelNumber,
                                              boolean mdMeter,
                                              ProfileMetadataResult metadataResult) throws Exception {

        // Pre-fetch meter ratios if MD
        MeterRatios meterRatios = mdMeter ? ratioService.readMeterRatios(modelNumber, meterSerial) : null;

        return rawRows.stream()
                .map(raw -> mapRow(raw, meterSerial, modelNumber, mdMeter, metadataResult, meterRatios))
                .collect(Collectors.toList());
    }

    private ProfileChannel2ReadingDTO mapRow(ProfileRowGeneric raw,
                                          String meterSerial,
                                          String modelNumber,
                                          boolean mdMeter,
                                          ProfileMetadataResult metadataResult,
                                          MeterRatios meterRatios) {

        ProfileChannel2ReadingDTO dto = new ProfileChannel2ReadingDTO();
        dto.setMeterSerial(meterSerial);
        dto.setModelNumber(modelNumber);

        // Timestamp
        // Raw data for debugging
//        dto.setRawData(raw.getValues().toString());
        dto.setRawData(null);

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
                try {
                    LocalDateTime tsInstant = timestampDecoder.toLocalDateTime(rawValue.toString());
                    dto.setEntryTimestamp(tsInstant);
                } catch (Exception e) {
                    // Fallback: use raw.getTimestamp() if parsing fails
                    dto.setEntryTimestamp(LocalDateTime.ofInstant(raw.getTimestamp(), ZoneId.systemDefault()));
                }
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

    private void setDtoField(ProfileChannel2ReadingDTO dto, String columnName, BigDecimal value) {
        switch (columnName.toLowerCase()) {
            case "total_import_active_energy" -> dto.setImportActiveEnergy(value.doubleValue());
            case "total_export_active_energy" -> dto.setExportActiveEnergy(value.doubleValue());
            default -> log.warn("Unknown column mapping: {}", columnName);
        }
    }
}
