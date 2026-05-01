package com.memmcol.hes.domain.profile.mappers;

import com.memmcol.hes.application.port.out.GenericDtoMappers;
import com.memmcol.hes.domain.profile.MeterRatios;
import com.memmcol.hes.domain.profile.ObisObjectType;
import com.memmcol.hes.domain.profile.ProfileMetadataResult;
import com.memmcol.hes.domain.profile.ProfileRowGeneric;
import com.memmcol.hes.dto.BillingEnergyHouseholdDTO;
import com.memmcol.hes.infrastructure.dlms.DlmsTimestampDecoder;
import com.memmcol.hes.service.MeterRatioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class BillingEnergyHouseholdMapper implements GenericDtoMappers<BillingEnergyHouseholdDTO> {
    private final MeterRatioService ratioService;
    private final DlmsTimestampDecoder dlmsTimestampDecoder;

    @Override
    public List<BillingEnergyHouseholdDTO> toDTO(List<ProfileRowGeneric> rawRows,
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
    public BillingEnergyHouseholdDTO mapRow(ProfileRowGeneric raw,
                                            String meterSerial,
                                            String modelNumber,
                                            boolean mdMeter,
                                            ProfileMetadataResult metadataResult,
                                            MeterRatios meterRatios) {
        BillingEnergyHouseholdDTO dto = new BillingEnergyHouseholdDTO();
        dto.setMeterSerial(meterSerial);
        dto.setMeterModel(modelNumber);

        for (Map.Entry<String, Object> entry : raw.getValues().entrySet()) {
            String obisWithAttr = entry.getKey();
            String baseObis = obisWithAttr.split("-")[0];
            Object rawValue = entry.getValue();
            if (rawValue == null) continue;

            ProfileMetadataResult.ProfilePersistenceInfo persistenceInfo = metadataResult.forPersistence(baseObis);
            if (persistenceInfo == null) continue;

            ObisObjectType objectType = persistenceInfo.getType();
            if (objectType == ObisObjectType.CLOCK) {
                dto.setEntryTimestamp(dlmsTimestampDecoder.decodeTimestamp(rawValue));
                continue;
            }

            double scaler = persistenceInfo.getScaler();
            String multiplyBy = persistenceInfo.getMultiplyBy();

            try {
                BigDecimal value = new BigDecimal(rawValue.toString());
                BigDecimal finalValue;

                if (objectType == ObisObjectType.NON_SCALER) {
                    finalValue = value;
                } else if (mdMeter && meterRatios != null) {
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
                ProfileMetadataResult.ProfileMappingInfo mappingInfo = metadataResult.forMapping(baseObis);
                if (mappingInfo != null) {
                    setDtoField(dto, mappingInfo.getColumnName(), finalValue);
                }
            } catch (NumberFormatException e) {
                log.error("NumberFormatException for obis={} value={}", baseObis, rawValue);
            }
        }

        dto.setReceivedAt(LocalDateTime.now());
        return dto;
    }

    @Override
    public void setDtoField(BillingEnergyHouseholdDTO dto, String columnName, BigDecimal value) {
        switch (columnName.toLowerCase()) {
            case "active_energy_import" -> dto.setActiveEnergyImport(value.doubleValue());
            case "active_energy_import_ongrid" -> dto.setActiveEnergyImportOngrid(value.doubleValue());
            case "active_energy_import_offgrid" -> dto.setActiveEnergyImportOffgrid(value.doubleValue());
            case "active_energy_export_ongrid" -> dto.setActiveEnergyExportOngrid(value.doubleValue());
            case "active_energy_export_offgrid" -> dto.setActiveEnergyExportOffgrid(value.doubleValue());
            case "active_energy_export" -> dto.setActiveEnergyExport(value.doubleValue());
            default -> log.warn("Unknown column mapping (billing energy hh): {}", columnName);
        }
    }
}

