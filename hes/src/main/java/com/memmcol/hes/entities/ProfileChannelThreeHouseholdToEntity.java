package com.memmcol.hes.entities;

import com.memmcol.hes.dto.ProfileChannelThreeHouseholdDTO;

public class ProfileChannelThreeHouseholdToEntity {
    public static ProfileChannelThreeHousehold toEntity(ProfileChannelThreeHouseholdDTO dto) {
        if (dto == null) return null;
        return ProfileChannelThreeHousehold.builder()
                .meterSerial(dto.getMeterSerial())
                .modelNumber(dto.getModelNumber())
                .entryTimestamp(dto.getEntryTimestamp())
                .activePowerL1(dto.getActivePowerL1())
                .activePowerL2(dto.getActivePowerL2())
                .activePowerL3(dto.getActivePowerL3())
                .powerFactorL1(dto.getPowerFactorL1())
                .powerFactorL2(dto.getPowerFactorL2())
                .powerFactorL3(dto.getPowerFactorL3())
                .gridFrequency(dto.getGridFrequency())
                .voltAngleL1L2(dto.getVoltAngleL1L2())
                .receivedAt(dto.getReceivedAt())
                .build();
    }
}
