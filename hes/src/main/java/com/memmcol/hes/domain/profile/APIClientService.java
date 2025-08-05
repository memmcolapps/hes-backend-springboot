package com.memmcol.hes.domain.profile;

import com.memmcol.hes.application.port.out.APIClientPort;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.nettyUtils.SessionManager;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.enums.ObjectType;
import gurux.dlms.objects.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Service
@Slf4j
@AllArgsConstructor
public class APIClientService implements APIClientPort {
    private final ObisMappingService obisMappingService;
    private final InstantaneousReadService readService;
    private final ReadCTPT readCTPT;

    @Override
    public Object readInstantaneous(String model, String meterSerial, String obisCombined) throws Exception {
        //Step 1:  Read raw value from meter
        Object rawValue = readService.readObisValue(model, meterSerial, obisCombined, true);
        if (rawValue == null) {
            throw new IllegalStateException("No value found for OBIS code: " + obisCombined);
        }

        // Step 2: Handle numeric values
        if (rawValue instanceof Integer || rawValue instanceof BigDecimal) {
            //Step 3:  Read CTPT
            MeterRatios meterRatios = readCTPT.readMeterRatios(model, meterSerial);
            log.info("MeterRatios: {}", meterRatios);

            //Step 4:  Read scaler and CTPT multiplier
            /**
             * Get "usesCtpt" from DB using meterSerial
             * If non-MD meter, usesCtpt = NONE
             * If MD meter, it will check the OBIS mapping table from the "purpose" column value
             */
            Double scaler = 1.00;
            String usesCtpt =  "CT";  //Default for MD meters. For non-MD is it NONE
            Map<String, MultiplierDTO> multiplier = obisMappingService.getScalerAndPurposeMap("model");
            if (multiplier.containsKey(obisCombined)) {
                MultiplierDTO data = multiplier.get(obisCombined);
                scaler = data.getScaler();
                usesCtpt = data.getPurpose();
                log.debug("Scaler: {}, Use CTPT?: {}", scaler, usesCtpt);
            }

            BigDecimal value = new BigDecimal(rawValue.toString());
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
        return rawValue;
    }
}
