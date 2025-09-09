package com.memmcol.hes.entities;

import com.memmcol.hes.dto.ProfileChannelOneDTO;

public class ProfileChannelOneToEntity {
    public static ProfileChannelOne toEntity(ProfileChannelOneDTO dto) {
        return ProfileChannelOne.builder()
                .entryTimestamp(dto.getEntryTimestamp())
                .meterSerial(dto.getMeterSerial())
                .modelNumber(dto.getModelNumber())
                .meterHealthIndicator(dto.getMeterHealthIndicator())
                .totalInstantaneousActivePower(dto.getTotalInstantaneousActivePower())
                .totalInstantaneousApparentPower(dto.getTotalInstantaneousApparentPower())
                .l1CurrentHarmonicThd(dto.getL1CurrentHarmonicThd())
                .l2CurrentHarmonicThd(dto.getL2CurrentHarmonicThd())
                .l3CurrentHarmonicThd(dto.getL3CurrentHarmonicThd())
                .l1VoltageHarmonicThd(dto.getL1VoltageHarmonicThd())
                .l2VoltageHarmonicThd(dto.getL2VoltageHarmonicThd())
                .l3VoltageHarmonicThd(dto.getL3VoltageHarmonicThd())
                .receivedAt(dto.getReceivedAt())
                .build();
    }
}

