package com.memmcol.hes.domain.profile.mappers;

import com.memmcol.hes.application.port.out.GenericDtoMappers;
import com.memmcol.hes.domain.profile.MeterRatios;
import com.memmcol.hes.domain.profile.ObisObjectType;
import com.memmcol.hes.domain.profile.ProfileMetadataResult;
import com.memmcol.hes.domain.profile.ProfileRowGeneric;
import com.memmcol.hes.dto.ProfileChannelThreeHouseholdDTO;
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
public class ProfileChannelThreeHouseholdMapper implements GenericDtoMappers<ProfileChannelThreeHouseholdDTO> {
    private static final String SINGLE_PHASE_MODEL_HINT = "SINGLE";
    private static final String SINGLE_PHASE_MODEL_HINT_ALT = "SNGLE";

    private final MeterRatioService ratioService;
    private final DlmsTimestampDecoder dlmsTimestampDecoder;

    @Override
    public List<ProfileChannelThreeHouseholdDTO> toDTO(List<ProfileRowGeneric> rawRows,
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
    public ProfileChannelThreeHouseholdDTO mapRow(ProfileRowGeneric raw,
                                                  String meterSerial,
                                                  String modelNumber,
                                                  boolean mdMeter,
                                                  ProfileMetadataResult metadataResult,
                                                  MeterRatios meterRatios) {
        ProfileChannelThreeHouseholdDTO dto = new ProfileChannelThreeHouseholdDTO();
        dto.setMeterSerial(meterSerial);
        dto.setModelNumber(modelNumber);

        boolean singlePhase = isSinglePhaseModel(modelNumber);

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
                    setDtoField(dto, mappingInfo.getColumnName(), finalValue, singlePhase);
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
    public void setDtoField(ProfileChannelThreeHouseholdDTO dto, String columnName, BigDecimal value) {
        setDtoField(dto, columnName, value, false);
    }

    private void setDtoField(ProfileChannelThreeHouseholdDTO dto, String columnName, BigDecimal value, boolean singlePhase) {
        String normalized = columnName.toLowerCase();

        if (singlePhase && !isSinglePhaseAllowedColumn(normalized)) {
            return;
        }

        switch (normalized) {
            case "active_power_l1" -> dto.setActivePowerL1(value.doubleValue());
            case "active_power_l2" -> dto.setActivePowerL2(value.doubleValue());
            case "active_power_l3" -> dto.setActivePowerL3(value.doubleValue());
            case "power_factor_l1" -> dto.setPowerFactorL1(value.doubleValue());
            case "power_factor_l2" -> dto.setPowerFactorL2(value.doubleValue());
            case "power_factor_l3" -> dto.setPowerFactorL3(value.doubleValue());
            case "grid_frequency" -> dto.setGridFrequency(value.doubleValue());
            default -> log.warn("Unknown column mapping (hh ch3): {}", columnName);
        }
    }

    private boolean isSinglePhaseAllowedColumn(String columnName) {
        return switch (columnName) {
            case "entry_timestamp", "active_power_l1", "active_power_l2",
                    "power_factor_l1", "power_factor_l2", "grid_frequency" -> true;
            default -> false;
        };
    }

    private boolean isSinglePhaseModel(String modelNumber) {
        if (modelNumber == null) {
            return false;
        }
        String normalized = modelNumber.toUpperCase();
        return normalized.contains(SINGLE_PHASE_MODEL_HINT) || normalized.contains(SINGLE_PHASE_MODEL_HINT_ALT);
    }
}
