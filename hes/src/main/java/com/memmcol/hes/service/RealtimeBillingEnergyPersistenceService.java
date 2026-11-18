package com.memmcol.hes.service;

import com.memmcol.hes.entities.DailyBillingEnergyHouseholdEntity;
import com.memmcol.hes.repository.DailyBillingEnergyHouseholdRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RealtimeBillingEnergyPersistenceService {

    private final DailyBillingEnergyHouseholdRepository repository;

    private static final String METER_CLOCK =
            "8;0.0:1.0.0.255;2;0";

    private static final String ACTIVE_ENERGY_IMPORT =
            "3;1.0.1.8.0.255;2;0";

    private static final String ACTIVE_ENERGY_IMPORT_ONGRID =
            "3;1.0.1.8.1.255;2;0";

    private static final String ACTIVE_ENERGY_IMPORT_OFFGRID =
            "3;1.0.1.8.2.255;2;0";

    private static final String ACTIVE_ENERGY_EXPORT =
            "3;1.0.2.8.0.255;2;0";

    private static final String ACTIVE_ENERGY_EXPORT_ONGRID =
            "3;1.0.2.8.1.255;2;0";

    private static final String ACTIVE_ENERGY_EXPORT_OFFGRID =
            "3;1.0.2.8.2.255;2;0";

    private static final DateTimeFormatter METER_CLOCK_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


    @Transactional
    public boolean saveIfComplete(
            String meterSerial,
            String meterModel,
            boolean md,
            Map<String, Map<String, Object>> readings
    ) {

        if (readings == null || readings.isEmpty()) {
            log.warn("No realtime readings received for meter {}", meterSerial);
            return false;
        }

        try {

//            // Meter clock is mandatory for both MD and Non-MD meters.
//            LocalDateTime entryTimestamp =
//                    parseMeterClock(getRequiredValue(readings, METER_CLOCK));
            // Meter clock is mandatory for both MD and Non-MD meters.

            LocalDateTime entryTimestamp =
                    parseMeterClock(getRequiredValue(readings, METER_CLOCK));

            // Check whether this meter already has a record for this meter-clock timestamp.
            // If it exists, preserve the existing record and skip this new record.
            if (repository.existsByMeterSerialAndEntryTimestamp(
                    meterSerial,
                    entryTimestamp
            )) {
                log.info(
                        "Billing energy record already exists for meter={} at {}. Skipping duplicate.",
                        meterSerial,
                        entryTimestamp
                );

                return false;
            }

            Double activeEnergyImport =
                    parseEnergyValue(getRequiredValue(readings, ACTIVE_ENERGY_IMPORT));

            Double activeEnergyImportOngrid =
                    parseEnergyValue(getRequiredValue(readings, ACTIVE_ENERGY_IMPORT_ONGRID));

            Double activeEnergyImportOffgrid =
                    parseEnergyValue(getRequiredValue(readings, ACTIVE_ENERGY_IMPORT_OFFGRID));

            Double activeEnergyExport = null;
            Double activeEnergyExportOngrid = null;
            Double activeEnergyExportOffgrid = null;

            // Export readings are mandatory only for MD meters.
            if (md) {

                activeEnergyExport =
                        parseEnergyValue(getRequiredValue(readings, ACTIVE_ENERGY_EXPORT));

                activeEnergyExportOngrid =
                        parseEnergyValue(getRequiredValue(readings, ACTIVE_ENERGY_EXPORT_ONGRID));

                activeEnergyExportOffgrid =
                        parseEnergyValue(getRequiredValue(readings, ACTIVE_ENERGY_EXPORT_OFFGRID));
            }

            DailyBillingEnergyHouseholdEntity entity =
                    DailyBillingEnergyHouseholdEntity.builder()
                            .meterSerial(meterSerial)
                            .meterModel(meterModel)
                            .entryTimestamp(entryTimestamp)
                            .activeEnergyImport(activeEnergyImport)
                            .activeEnergyImportOngrid(activeEnergyImportOngrid)
                            .activeEnergyImportOffgrid(activeEnergyImportOffgrid)
                            .activeEnergyExport(activeEnergyExport)
                            .activeEnergyExportOngrid(activeEnergyExportOngrid)
                            .activeEnergyExportOffgrid(activeEnergyExportOffgrid)
                            .build();

            repository.save(entity);

            log.info(
                    "Realtime billing energy saved successfully for meter {} at {}",
                    meterSerial,
                    entryTimestamp
            );

            return true;

        } catch (Exception ex) {

            log.warn(
                    "Realtime billing energy not saved for meter {}. Reason: {}",
                    meterSerial,
                    ex.getMessage()
            );

            return false;
        }
    }


    private Object getRequiredValue(
            Map<String, Map<String, Object>> readings,
            String obisCode
    ) {

        Map<String, Object> reading = readings.get(obisCode);

        if (reading == null) {
            throw new IllegalArgumentException(
                    "Missing mandatory OBIS reading: " + obisCode
            );
        }

        Object statusCode = reading.get("statuscode");

        if (statusCode == null || !"0".equals(statusCode.toString())) {
            throw new IllegalArgumentException(
                    "OBIS reading failed: " + obisCode
            );
        }

        Object value = reading.get("value");

        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(
                    "OBIS reading has an empty value: " + obisCode
            );
        }

        return value;
    }


    private LocalDateTime parseMeterClock(Object value) {

        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }

        return LocalDateTime.parse(
                value.toString().trim(),
                METER_CLOCK_FORMATTER
        );
    }


    private Double parseEnergyValue(Object value) {

        double energy = Double.parseDouble(value.toString().trim());

        if (!Double.isFinite(energy)) {
            throw new IllegalArgumentException(
                    "Invalid energy reading: " + value
            );
        }

        return energy;
    }
}
