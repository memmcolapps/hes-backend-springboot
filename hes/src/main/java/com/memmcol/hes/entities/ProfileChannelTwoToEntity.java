package com.memmcol.hes.entities;

import com.memmcol.hes.dto.ProfileChannelTwoDTO;

public class ProfileChannelTwoToEntity {

    public static ProfileChannelTwo toEntity(ProfileChannelTwoDTO dto) {
        if (dto == null) {
            return null;
        }
        return ProfileChannelTwo.builder()
                .meterSerial(dto.getMeterSerial())
                .modelNumber(dto.getModelNumber())
                .entryTimestamp(dto.getEntryTimestamp())
                .meterHealthIndicator(dto.getMeterHealthIndicator())
                .activeEnergyImport(dto.getActiveEnergyImport())
                .activeEnergyImportRate1(dto.getActiveEnergyImportRate1())
                .activeEnergyImportRate2(dto.getActiveEnergyImportRate2())
                .activeEnergyImportRate3(dto.getActiveEnergyImportRate3())
                .activeEnergyImportRate4(dto.getActiveEnergyImportRate4())
                .activeEnergyCombinedTotal(dto.getActiveEnergyCombinedTotal())
                .activeEnergyExport(dto.getActiveEnergyExport())
                .reactiveEnergyImport(dto.getReactiveEnergyImport())
                .reactiveEnergyExport(dto.getReactiveEnergyExport())
                .apparentEnergyImport(dto.getApparentEnergyImport())
                .apparentEnergyExport(dto.getApparentEnergyExport())
                .receivedAt(dto.getReceivedAt())
                .build();
    }

    public static ProfileChannelTwoDTO toDTO(ProfileChannelTwo entity) {
        if (entity == null) {
            return null;
        }
        return ProfileChannelTwoDTO.builder()
                .meterSerial(entity.getMeterSerial())
                .modelNumber(entity.getModelNumber())
                .entryTimestamp(entity.getEntryTimestamp())
                .meterHealthIndicator(entity.getMeterHealthIndicator())
                .activeEnergyImport(entity.getActiveEnergyImport())
                .activeEnergyImportRate1(entity.getActiveEnergyImportRate1())
                .activeEnergyImportRate2(entity.getActiveEnergyImportRate2())
                .activeEnergyImportRate3(entity.getActiveEnergyImportRate3())
                .activeEnergyImportRate4(entity.getActiveEnergyImportRate4())
                .activeEnergyCombinedTotal(entity.getActiveEnergyCombinedTotal())
                .activeEnergyExport(entity.getActiveEnergyExport())
                .reactiveEnergyImport(entity.getReactiveEnergyImport())
                .reactiveEnergyExport(entity.getReactiveEnergyExport())
                .apparentEnergyImport(entity.getApparentEnergyImport())
                .apparentEnergyExport(entity.getApparentEnergyExport())
                .receivedAt(entity.getReceivedAt())
                .build();
    }
}