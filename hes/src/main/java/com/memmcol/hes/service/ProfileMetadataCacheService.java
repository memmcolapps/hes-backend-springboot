package com.memmcol.hes.service;

import com.memmcol.hes.model.ProfileMetadataDTO;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class ProfileMetadataCacheService {
    private static final String CACHE_NAME = "profileMetadata";

    private final CacheManager cacheManager;

    public ProfileMetadataCacheService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void put(String meterSerial, String obisCode, ProfileMetadataDTO metadata) {
        String key = buildKey(meterSerial, obisCode);
        cacheManager.getCache(CACHE_NAME).put(key, metadata);
    }

    public Optional<ProfileMetadataDTO> get(String meterSerial, String obisCode) {
        String key = buildKey(meterSerial, obisCode);
        Cache.ValueWrapper wrapper = cacheManager.getCache(CACHE_NAME).get(key);
        return Optional.ofNullable(wrapper != null ? (ProfileMetadataDTO) wrapper.get() : null);
    }

    public void evict(String meterSerial, String obisCode) {
        String key = buildKey(meterSerial, obisCode);
        Objects.requireNonNull(cacheManager.getCache(CACHE_NAME)).evict(key);
    }

    private String buildKey(String serial, String obis) {
        return serial + "::" + obis;
    }
}
