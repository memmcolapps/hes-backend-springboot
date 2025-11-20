package com.memmcol.hes.service;

import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.model.ObisMapping;
import com.memmcol.hes.nettyUtils.SessionManagerMultiVendor;
import com.memmcol.hes.repository.MeterRepository;
import com.memmcol.hes.repository.ObisMappingRepository;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.enums.ObjectType;
import gurux.dlms.enums.Unit;
import gurux.dlms.objects.GXDLMSDemandRegister;
import gurux.dlms.objects.GXDLMSExtendedRegister;
import gurux.dlms.objects.GXDLMSRegister;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.memmcol.hesTraining.services.MeterReadingService.getUnitSymbol;

@Service
@RequiredArgsConstructor
@Slf4j
public class ObisScalerService {

    private final DlmsReaderUtils dlmsReaderUtils;
    private final ObisMappingRepository obisMappingRepository;
    private final SessionManagerMultiVendor sessionManagerMultiVendor;

    /**
     * Update scaler & unit for all OBIS mappings of a given meter
     * Returns JSON report of failures
     */
    public Map<String, Object> updateScalerUnitForMeter(String meterSerial, String model) throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        List<Map<String, Object>> failures = new ArrayList<>();
        int success = 0;

        List<Integer> classIds = List.of(4, 3);
        // Step 2: Get relevant OBIS mappings
        List<ObisMapping> mappings = obisMappingRepository
                .findByModelAndClassIdInAndAttributeIndex(
                        model,
                        classIds, // class_id IN (3,4)
                        2                     // attribute_index
                );

        if (mappings.isEmpty()) {
            log.warn("No OBIS mappings found for meter {} with model {}", meterSerial, model);
            report.put("status", "No OBIS mappings found");
            return report;
        }

        // Step 3: Get DLMS client for this meter
        GXDLMSClient client = sessionManagerMultiVendor.getOrCreateClient(meterSerial);
        if (client == null) {
            log.error("No active DLMS session for meter: {}", meterSerial);
            report.put("status", "No active DLMS session");
            return report;
        }

        // Step 4: Loop through mappings
        for (ObisMapping mapping : mappings) {
            try {
                Map<String, Object> scalerUnit = readScalerUnit(
                        client,
                        meterSerial,
                        mapping.getObisCode(),
                        mapping.getClassId(),
                        mapping.getAttributeIndex()
                );

                double scaler = (double) scalerUnit.get("scaler");
                String unit = (String) scalerUnit.get("units");

                // Adjust for kilo units
                if (Arrays.asList("KW", "KVA", "KVar", "KWh", "KVAh", "KVarh").contains(unit)) {
                    scaler = scaler / 1000.0;
                }

                // Update DB
                mapping.setScaler(scaler);
                mapping.setUnit(unit);
                obisMappingRepository.save(mapping);

                success++;

                log.info("Updated {} for meter {}: scaler={}, unit={}", mapping.getObisCode(),
                        meterSerial, scaler, unit);

            } catch (Exception ex) {
                log.error("Failed to read scaler/unit for OBIS {} on meter {}: {}",
                        mapping.getObisCode(), meterSerial, ex.getMessage());
                failures.add(Map.of(
                        "meterSerial", meterSerial,
                        "obisCode", mapping.getObisCode(),
                        "error", ex.getMessage()
                ));
            }
        }

        report.put("status", "Completed");
        report.put("meterSerial", meterSerial);
        report.put("totalMappings", mappings.size());
        report.put("success count", success);
        report.put("failures count", failures.size());
        report.put("failures", failures);

        log.info("One-off OBIS scaler/unit update completed for meter: {}", meterSerial);
        return report;
    }

    private Map<String, Object> readScalerUnit(GXDLMSClient client, String meterSerial, String captureObis,
                                               int classId, int attrIndex) throws Exception {
        double scaler = 1.0;
        String units = "";

        ObjectType type = ObjectType.forValue(classId);
        switch (type) {
            case REGISTER -> {
                GXDLMSRegister reg = new GXDLMSRegister();
                reg.setLogicalName(captureObis);
                dlmsReaderUtils.readScalerUnit(client, meterSerial, reg, 3);
                scaler = (reg.getScaler() == 0) ? 1.0 : reg.getScaler();
                units = getUnitSymbol(reg.getUnit());
            }
            case DEMAND_REGISTER -> {
                GXDLMSDemandRegister dr = new GXDLMSDemandRegister();
                dr.setLogicalName(captureObis);
                dlmsReaderUtils.readScalerUnit(client, meterSerial, dr, 4);
                scaler = (dr.getScaler() == 0) ? 1.0 : dr.getScaler();
                units = getUnitSymbol(dr.getUnit());
            }
            case EXTENDED_REGISTER -> {
                GXDLMSExtendedRegister dr = new GXDLMSExtendedRegister();
                dr.setLogicalName(captureObis);
                dlmsReaderUtils.readScalerUnit(client, meterSerial, dr, 4);
                scaler = (dr.getScaler() == 0) ? 1.0 : dr.getScaler();
                units = getUnitSymbol(dr.getUnit());
                dlmsReaderUtils.readScalerUnit(client, meterSerial, dr, 3);
                dlmsReaderUtils.readScalerUnit(client, meterSerial, dr, 5);
            }
            default -> log.warn("Unsupported object type for OBIS: {}, Class ID: {}", captureObis, classId);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("captureObis", captureObis);
        response.put("scaler", scaler);
        response.put("units", units);
        return response;
    }

}
