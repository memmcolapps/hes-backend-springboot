package com.memmcol.hes.entities;

import com.memmcol.hes.dto.ProfileChannelOneHouseholdDTO;

public class ProfileChannelOneHouseholdToEntity {
    public static ProfileChannelOneHousehold toEntity(ProfileChannelOneHouseholdDTO dto) {
        if (dto == null) return null;
        return ProfileChannelOneHousehold.builder()
                .meterSerial(dto.getMeterSerial())
                .modelNumber(dto.getModelNumber())
                .entryTimestamp(dto.getEntryTimestamp())
                .activeEnergyImport(dto.getActiveEnergyImport())
                .activeEnergyImportOnGrid(dto.getActiveEnergyImportOnGrid())
                .activeEnergyImportOffGrid(dto.getActiveEnergyImportOffGrid())
                .activeEnergyExport(dto.getActiveEnergyExport())
                .activeEnergyExportOnGrid(dto.getActiveEnergyExportOnGrid())
                .activeEnergyExportOffGrid(dto.getActiveEnergyExportOffGrid())
                .receivedAt(dto.getReceivedAt())
                .build();
    }
}

