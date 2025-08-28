package com.memmcol.hes.service;

import com.memmcol.hes.model.ProfileChannel2ReadingDTO;
import com.memmcol.hes.model.ProfileMetadataDTO;

import java.time.LocalDateTime;
import java.util.List;

public class ProfileRowMapper {
    /**
     * Parses and maps a DLMS row using the known column metadata.
     * Assumes: columns.size == values.size
     */
    public static ProfileChannel2ReadingDTO mapChannel2(
            List<Object> values,
            List<ProfileMetadataDTO.ColumnDTO> columns,
            String meterSerial,
            String modelNumber,
            int entryIndex,
            LocalDateTime timestamp
    ) {
        Double reactiveEnergy = null;
        Double activeEnergy = null;

        // Loop over the values and match with OBIS
        for (int i = 0; i < values.size(); i++) {
            Object val = values.get(i);
            ProfileMetadataDTO.ColumnDTO column = columns.get(i);
            String obis = column.getObis();
            double scaler;
            if (column.getScaler() != 0) {
                scaler = column.getScaler();
            } else scaler = 1.0;

            if (val instanceof Number numVal) {
                double scaled = numVal.doubleValue() * scaler;

                switch (obis) {
                    case "1.0.2.8.0.255" -> reactiveEnergy = scaled;
                    case "1.0.1.8.0.255" -> activeEnergy = scaled;
                    // Add other OBIS mappings here as needed
                }
            }
        }

        return ProfileChannel2ReadingDTO.builder()
                .meterSerial(meterSerial)
                .modelNumber(modelNumber)
                .entryIndex(entryIndex)
                .profileTimestamp(timestamp)
                .reactiveEnergyKvarh(reactiveEnergy)
                .activeEnergyKwh(activeEnergy)
                .rawData(values.toString()) // optional
                .receivedAt(LocalDateTime.now())
                .build();
    }
}
