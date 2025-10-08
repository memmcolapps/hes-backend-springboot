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
                .totalImportActiveEnergy(dto.getTotalImportActiveEnergy())
                .totalExportActiveEnergy(dto.getTotalExportActiveEnergy())
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
                .totalImportActiveEnergy(entity.getTotalImportActiveEnergy())
                .totalExportActiveEnergy(entity.getTotalExportActiveEnergy())
                .receivedAt(entity.getReceivedAt())
                .build();
    }
}