package com.memmcol.hes.service;

import com.memmcol.hes.model.ModelProfileMetadata;
import com.memmcol.hes.model.ProfileMetadataDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProfileMetadataMapper {
    public static ProfileMetadataDTO map(int startIndex, List<ModelProfileMetadata> metadataList) {
        List<ProfileMetadataDTO.ColumnDTO> columns = metadataList.stream()
                .map(meta -> ProfileMetadataDTO.ColumnDTO.builder()
                        .obis(meta.getCaptureObis())
                        .classId(meta.getClassId())
                        .attributeIndex(meta.getAttributeIndex())
                        .scaler(meta.getScaler())
                        .unit(meta.getUnit())
                        .build())
                .toList();

        return ProfileMetadataDTO.builder()
                .entriesInUse(startIndex)
                .columns(columns)
                .build();
    }
}
