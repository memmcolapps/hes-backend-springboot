package com.memmcol.hes.domain.profile;

import com.memmcol.hes.model.ModelProfileMetadata;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *
 */
public class ProfileMetadataResult {
    private final List<ModelProfileMetadata> metadataList;
    private final Map<String, Double> scalers;

    public ProfileMetadataResult(List<ModelProfileMetadata> metadataList) {
        this.metadataList = metadataList;
        this.scalers = metadataList.stream()
                .collect(Collectors.toMap(
                        ModelProfileMetadata::getCaptureObis,
                        ModelProfileMetadata::getScaler,
                        (a, b) -> a // In case of duplicate OBIS
                ));
    }

    public List<ModelProfileMetadata> getMetadataList() {
        return metadataList;
    }


    public Map<String, Double> getScalers() {
        return scalers;
    }
}