package com.memmcol.hes.domain.profile;

import com.memmcol.hes.model.ModelProfileMetadata;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *
 */
@Getter
@AllArgsConstructor
public class ProfileMetadataResult {
    public static Object ProfileMappingInfo;
    private final List<ModelProfileMetadata> metadataList;

    /**
     * Returns all mapping info as Map.
     */
    public Map<String, ProfileMappingInfo> forMapping() {
        return metadataList.stream()
                .collect(Collectors.toMap(
                        ModelProfileMetadata::getCaptureObis,
                        m -> new ProfileMappingInfo(
                                m.getCaptureIndex(),
                                m.getColumnName()
                        ),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    /**
     * Returns the mapping info for a specific OBIS code (or OBIS-attribute key).
     */
    public ProfileMappingInfo forMapping(String key) {
        String obis = key.split("-")[0];
        return metadataList.stream()
                .filter(m -> m.getCaptureObis().equals(obis))
                .findFirst()
                .map(m -> new ProfileMappingInfo(m.getCaptureIndex(), m.getColumnName()))
                .orElse(null);
    }

    /**
     * Returns all persistence info as Map.
     */
    public Map<String, ProfilePersistenceInfo> forPersistence() {
        return metadataList.stream()
                .collect(Collectors.toMap(
                        ModelProfileMetadata::getCaptureObis,
                        m -> new ProfilePersistenceInfo(
                                m.getScaler(),
                                m.getMultiplyBy(),
                                m.getType()
                        ),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    /**
     * Returns the persistence info for a specific OBIS code (or OBIS-attribute key).
     */
    public ProfilePersistenceInfo forPersistence(String key) {
        String obis = key.split("-")[0];
        return metadataList.stream()
                .filter(m -> m.getCaptureObis().equals(obis))
                .findFirst()
                .map(m -> new ProfilePersistenceInfo(m.getScaler(), m.getMultiplyBy(), m.getType()))
                .orElse(null);
    }


    @Getter
    @AllArgsConstructor
    public static class ProfileMappingInfo {
        private final int captureIndex;
        private final String columnName;

    }

    @Getter
    @AllArgsConstructor
    public static class ProfilePersistenceInfo {
        private final Double scaler;       // Changed from double to Double so we can detect null
        private final String multiplyBy;   // Changed to String since CTPT looks like text
        private final ObisObjectType type;

        public double getScaler() {
            return scaler != null ? scaler : 1.0;
        }

        public String getMultiplyBy() {
            return multiplyBy != null ? multiplyBy : "NONE";
        }

        public ObisObjectType getType() {
            return type != null ? type : ObisObjectType.NON_SCALER;
        }

    }
}