package com.memmcol.hes.domain.profile.mappers;

import com.memmcol.hes.application.port.out.GenericDtoMappers;
import com.memmcol.hes.domain.profile.MeterRatios;
import com.memmcol.hes.domain.profile.ObisObjectType;
import com.memmcol.hes.domain.profile.ProfileMetadataResult;
import com.memmcol.hes.domain.profile.ProfileRowGeneric;
import com.memmcol.hes.dto.ProfileChannelTwoHouseholdDTO;
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
public class ProfileChannelTwoHouseholdMapper implements GenericDtoMappers<ProfileChannelTwoHouseholdDTO> {
    private final MeterRatioService ratioService;
    private final DlmsTimestampDecoder dlmsTimestampDecoder;

    @Override
    public List<ProfileChannelTwoHouseholdDTO> toDTO(List<ProfileRowGeneric> rawRows,
                                                     String meterSerial,
                                                     String modelNumber,
                                                     boolean mdMeter,
                                                     ProfileMetadataResult metadataResult) throws Exception {
        MeterRatios meterRatios = mdMeter ? ratioService.readMeterRatios(modelNumber, meterSerial) : null;
        return rawRows.stream()
                .map(raw -> mapRow(raw, meterSerial, modelNumber, mdMeter, metadataResult, meterRatios))
                .collect(Collectors.toList());
    }

    @Override
    public ProfileChannelTwoHouseholdDTO mapRow(ProfileRowGeneric raw,
                                                String meterSerial,
                                                String modelNumber,
                                                boolean mdMeter,
                                                ProfileMetadataResult metadataResult,
                                                MeterRatios meterRatios) {
        ProfileChannelTwoHouseholdDTO dto = new ProfileChannelTwoHouseholdDTO();
        dto.setMeterSerial(meterSerial);
        dto.setModelNumber(modelNumber);

        for (Map.Entry<String, Object> entry : raw.getValues().entrySet()) {
            String obisWithAttr = entry.getKey();
            String obisCode = obisWithAttr.split("-")[0];
            Object rawValue = entry.getValue();
            if (rawValue == null) continue;

            ProfileMetadataResult.ProfilePersistenceInfo persistenceInfo = metadataResult.forPersistence(obisCode);
            if (persistenceInfo == null) continue;

            ObisObjectType objectType = persistenceInfo.getType();
            if (objectType == ObisObjectType.CLOCK) {
                LocalDateTime tsInstant;
                switch (rawValue) {
                    case LocalDateTime localDateTime -> tsInstant = localDateTime;
                    case String s -> tsInstant = LocalDateTime.parse(s);
                    case byte[] bytes -> tsInstant = dlmsTimestampDecoder.decode(bytes);
                    default -> {
                        log.warn("Unexpected timestamp type: {} for OBIS={}, using fallback",
                                rawValue.getClass().getName(), obisCode);
                        tsInstant = LocalDateTime.ofInstant(raw.getTimestamp(), ZoneId.systemDefault());
                    }
                }
                dto.setEntryTimestamp(tsInstant.truncatedTo(ChronoUnit.SECONDS));
                continue;
            }

            double scaler = persistenceInfo.getScaler();
            String multiplyBy = persistenceInfo.getMultiplyBy();

            try {
                BigDecimal value = new BigDecimal(rawValue.toString());
                BigDecimal finalValue;

                if (objectType == ObisObjectType.NON_SCALER) {
                    finalValue = value;
                } else if (mdMeter) {
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

                finalValue = finalValue.setScale(2, RoundingMode.HALF_UP);

                ProfileMetadataResult.ProfileMappingInfo mappingInfo = metadataResult.forMapping(obisCode);
                if (mappingInfo != null) {
                    setDtoField(dto, mappingInfo.getColumnName(), finalValue);
                }
            } catch (NumberFormatException e) {
                log.error("NumberFormatException for obis={} multiplyBy={} type={} value={}",
                        obisCode, multiplyBy, objectType, rawValue);
            }
        }

        dto.setReceivedAt(LocalDateTime.now());
        return dto;
    }

    @Override
    public void setDtoField(ProfileChannelTwoHouseholdDTO dto, String columnName, BigDecimal value) {
        switch (columnName.toLowerCase()) {
            case "voltage_l1" -> dto.setVoltageL1(value.doubleValue());
            case "voltage_l2" -> dto.setVoltageL2(value.doubleValue());
            case "voltage_l3" -> dto.setVoltageL3(value.doubleValue());
            case "current_l1" -> dto.setCurrentL1(value.doubleValue());
            case "current_l2" -> dto.setCurrentL2(value.doubleValue());
            case "current_l3" -> dto.setCurrentL3(value.doubleValue());
            default -> log.warn("Unknown column mapping (hh ch2): {}", columnName);
        }
    }
}

