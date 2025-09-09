package com.memmcol.hes.service;

import com.memmcol.hes.model.ProfileChannel2Reading;
import com.memmcol.hes.model.ProfileChannel2ReadingDTO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProfileChannel2ReadingMapper {
    public static ProfileChannel2Reading toEntity(ProfileChannel2ReadingDTO dto) {
        return ProfileChannel2Reading.builder()
                .meterSerial(dto.getMeterSerial())
                .modelNumber(dto.getModelNumber())
                .entryIndex(dto.getEntryIndex())
                .entryTimestamp(dto.getEntryTimestamp())
                .exportActiveEnergy(dto.getExportActiveEnergy())
                .importActiveEnergy(dto.getImportActiveEnergy())
                .rawData(dto.getRawData())
                .receivedAt(dto.getReceivedAt())
                .build();
    }

    public static List<ProfileChannel2Reading> toEntityList(List<ProfileChannel2ReadingDTO> dtoList) {
        return dtoList.stream()
                .map(ProfileChannel2ReadingMapper::toEntity)
                .collect(Collectors.toList());
    }
}
