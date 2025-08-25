package com.memmcol.hes.cache;

import lombok.AllArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@AllArgsConstructor
public class CacheEvictionService {
    private final CacheManager cacheManager;

    public void clearProfileMetadataCache() {
        if (cacheManager.getCache("profileMetadata") != null) {
            Objects.requireNonNull(cacheManager.getCache("profileMetadata")).clear();  // Clears all entries in that Caffeine cache
        }
    }

    public void clearProfileCapturePeriodCache() {
        if (cacheManager.getCache("profileCapturePeriod") != null) {
            Objects.requireNonNull(cacheManager.getCache("profileCapturePeriod")).clear();  // Clears all entries in that Caffeine cache
        }
    }

    public void clearLastProfileTimestampCache() {
        if (cacheManager.getCache("lastProfileTimestamp") != null) {
            Objects.requireNonNull(cacheManager.getCache("lastProfileTimestamp")).clear();  // Clears all entries in that Caffeine cache
        }
    }


}
