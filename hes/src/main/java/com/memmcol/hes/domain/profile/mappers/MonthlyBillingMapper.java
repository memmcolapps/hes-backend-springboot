package com.memmcol.hes.domain.profile.mappers;

import com.memmcol.hes.application.port.out.GenericDtoMappers;
import com.memmcol.hes.domain.profile.MeterRatios;
import com.memmcol.hes.domain.profile.ObisObjectType;
import com.memmcol.hes.domain.profile.ProfileMetadataResult;
import com.memmcol.hes.domain.profile.ProfileRowGeneric;
import com.memmcol.hes.dto.MonthlyBillingDTO;
import com.memmcol.hes.infrastructure.dlms.DlmsTimestampDecoder;
import com.memmcol.hes.service.MeterRatioService;
import gurux.dlms.internal.GXCommon;
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
public class MonthlyBillingMapper implements GenericDtoMappers<MonthlyBillingDTO> {
    private final MeterRatioService ratioService;
    private final DlmsTimestampDecoder dlmsTimestampDecoder;

    @Override
    public List<MonthlyBillingDTO> toDTO(List<ProfileRowGeneric> rawRows, String meterSerial, String modelNumber, boolean mdMeter, ProfileMetadataResult metadataResult) throws Exception {
        // Pre-fetch meter ratios if MD
        MeterRatios meterRatios = mdMeter ? ratioService.readMeterRatios(modelNumber, meterSerial) : null;

        return rawRows.stream()
                .map(raw -> mapRow(raw, meterSerial, modelNumber, mdMeter, metadataResult, meterRatios))
                .collect(Collectors.toList());
    }

    @Override
    public MonthlyBillingDTO mapRow(ProfileRowGeneric raw, String meterSerial, String modelNumber, boolean mdMeter, ProfileMetadataResult captureObjects, MeterRatios meterRatios) {
        MonthlyBillingDTO dto = new MonthlyBillingDTO();
        dto.setMeterSerial(meterSerial);
        dto.setMeterModel(modelNumber);

        // Iterate over all OBIS values
        for (Map.Entry<String, Object> entry : raw.getValues().entrySet()) {
            String obisWithAttr = entry.getKey();                     // e.g. 1.0.129.6.0.255-2
            String baseObis     = obisWithAttr.split("-")[0];         // e.g. 1.0.129.6.0.255
            Object rawValue     = entry.getValue();

            if (rawValue == null) continue;

            // Get persistence info (scaler, multiplyBy)
            ProfileMetadataResult.ProfilePersistenceInfo persistenceInfo = captureObjects.forPersistence(baseObis);
            if (persistenceInfo == null) continue;

            double scaler = persistenceInfo.getScaler();
            String multiplyBy = persistenceInfo.getMultiplyBy();
            ObisObjectType objectType = persistenceInfo.getType();  //1️⃣ Identify object type from metadata. Possible: CLOCK, NON_SCALER, SCALER

            // 👉 Custom handling for monthly demand: 1.0.129.6.0.255 and 1.0.15.6.0.255
            if (baseObis.equals("1.0.129.6.0.255")) {
                if (obisWithAttr.endsWith("-2")) {
                    BigDecimal value = new BigDecimal(rawValue.toString());
                    BigDecimal scaledValue = value.multiply(BigDecimal.valueOf(scaler))
                            .multiply(BigDecimal.valueOf(meterRatios.getCtptRatio()));
                    dto.setTotalApparentDemand(scaledValue.doubleValue());
                    continue;
                }
                if (obisWithAttr.endsWith("-5")) {
                    assert rawValue instanceof byte[];
                    LocalDateTime clock = dlmsTimestampDecoder.decode((byte[]) rawValue);
                    dto.setTotalApparentDemandTime(clock);
                    continue;
                }
            }
            if (baseObis.equals("1.0.15.6.0.255")) {
                if (obisWithAttr.endsWith("-2")) {
                    BigDecimal value = new BigDecimal(rawValue.toString());
                    BigDecimal scaledValue = value.multiply(BigDecimal.valueOf(scaler))
                            .multiply(BigDecimal.valueOf(meterRatios.getCtptRatio()));
                    dto.setActiveMaximumDemand(scaledValue.doubleValue());
                    continue;
                }
                if (obisWithAttr.endsWith("-5")) {
                    assert rawValue instanceof byte[];
                    LocalDateTime clock = dlmsTimestampDecoder.decode((byte[]) rawValue);
                    dto.setImportActiveMdTime(clock);
                    continue;
                }
            }

            // Set entry timestamp and other timestamp from CLOCK OBIS
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
                                rawValue.getClass().getName(), baseObis);
                        tsInstant = LocalDateTime.ofInstant(raw.getTimestamp(), ZoneId.systemDefault());
                    }
                }
                dto.setEntryTimestamp(tsInstant);
                continue;
            }

            BigDecimal finalValue = BigDecimal.ONE;
            BigDecimal value = BigDecimal.ONE;
            try {
                value = new BigDecimal(rawValue.toString());

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
                ProfileMetadataResult.ProfileMappingInfo mappingInfo = captureObjects.forMapping(baseObis);
                if (mappingInfo != null) {
                    setDtoField(dto, mappingInfo.getColumnName(), finalValue);
                }
            } catch (NumberFormatException e) {
                log.error("Rawvalue: {}, Value: {}, Obis code: {}, multiplyBy: {}, objectType: {}, NumberFormatException {}", GXCommon.toHex((byte[]) rawValue),  value, baseObis, multiplyBy, objectType, e.getMessage());
            }
        }
        dto.setReceivedAt(LocalDateTime.now());
        return dto;
    }

    @Override
    public void setDtoField(MonthlyBillingDTO dto, String columnName, BigDecimal value) {
        switch (columnName.toLowerCase()) {
            case "total_absolute_active_energy" -> dto.setTotalAbsoluteActiveEnergy(value.doubleValue());
            case "export_active_energy" -> dto.setExportActiveEnergy(value.doubleValue());
            case "import_active_energy" -> dto.setImportActiveEnergy(value.doubleValue());
            case "import_reactive_energy" -> dto.setImportReactiveEnergy(value.doubleValue());
            case "export_reactive_energy" -> dto.setExportReactiveEnergy(value.doubleValue());
            case "remaining_credit_amount" -> dto.setRemainingCreditAmount(value.doubleValue());
            case "import_active_md" -> dto.setImportActiveMd(value.doubleValue());
            case "t1_active_energy" -> dto.setT1ActiveEnergy(value.doubleValue());
            case "t2_active_energy" -> dto.setT2ActiveEnergy(value.doubleValue());
            case "t3_active_energy" -> dto.setT3ActiveEnergy(value.doubleValue());
            case "t4_active_energy" -> dto.setT4ActiveEnergy(value.doubleValue());
            case "total_active_energy" -> dto.setTotalActiveEnergy(value.doubleValue());
            case "total_apparent_energy" -> dto.setTotalApparentEnergy(value.doubleValue());
            case "t1_total_apparent_energy" -> dto.setT1TotalApparentEnergy(value.doubleValue());
            case "t2_total_apparent_energy" -> dto.setT2TotalApparentEnergy(value.doubleValue());
            case "t3_total_apparent_energy" -> dto.setT3TotalApparentEnergy(value.doubleValue());
            case "t4_total_apparent_energy" -> dto.setT4TotalApparentEnergy(value.doubleValue());
            case "active_maximum_demand" -> dto.setActiveMaximumDemand(value.doubleValue());
            case "total_apparent_demand" -> dto.setTotalApparentDemand(value.doubleValue());
            default -> log.warn("Unknown column mapping: {}", columnName);
        }
    }
}
