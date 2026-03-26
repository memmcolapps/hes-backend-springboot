package com.memmcol.hes.entities;

import com.memmcol.hes.dto.ProfileChannelOneDTO;

public class ProfileChannelOneToEntity {
    public static ProfileChannelOne toEntity(ProfileChannelOneDTO dto) {
        return ProfileChannelOne.builder()
                .entryTimestamp(dto.getEntryTimestamp())
                .meterSerial(dto.getMeterSerial())
                .modelNumber(dto.getModelNumber())
                .meterHealthIndicator(dto.getMeterHealthIndicator())
                .instantaneousActivePower(dto.getInstantaneousActivePower())
                .instantaneousApparentPower(dto.getInstantaneousApparentPower())
                .instantaneousVoltageL1(dto.getInstantaneousVoltageL1())
                .instantaneousVoltageL2(dto.getInstantaneousVoltageL2())
                .instantaneousVoltageL3(dto.getInstantaneousVoltageL3())
                .instantaneousCurrentL1(dto.getInstantaneousCurrentL1())
                .instantaneousCurrentL2(dto.getInstantaneousCurrentL2())
                .instantaneousCurrentL3(dto.getInstantaneousCurrentL3())
                .instantaneousNetFrequency(dto.getInstantaneousNetFrequency())
                .instantaneousPowerFactor(dto.getInstantaneousPowerFactor())
                .instantaneousReactiveExport(dto.getInstantaneousReactiveExport())
                .instantaneousReactiveImport(dto.getInstantaneousReactiveImport())
                .receivedAt(dto.getReceivedAt())
                .build();
    }
}

