package com.memmcol.hes.entities;

import com.memmcol.hes.dto.ProfileChannelTwoHouseholdDTO;

public class ProfileChannelTwoHouseholdToEntity {
    public static ProfileChannelTwoHousehold toEntity(ProfileChannelTwoHouseholdDTO dto) {
        if (dto == null) return null;
        return ProfileChannelTwoHousehold.builder()
                .meterSerial(dto.getMeterSerial())
                .modelNumber(dto.getModelNumber())
                .entryTimestamp(dto.getEntryTimestamp())
                .voltageL1(dto.getVoltageL1())
                .voltageL2(dto.getVoltageL2())
                .voltageL3(dto.getVoltageL3())
                .currentL1(dto.getCurrentL1())
                .currentL2(dto.getCurrentL2())
                .currentL3(dto.getCurrentL3())
                .receivedAt(dto.getReceivedAt())
                .build();
    }
}

