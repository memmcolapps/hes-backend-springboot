package com.memmcol.hes.trackByTimestamp;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ProfileTimestampCacheService {

    private static final String CACHE_NAME = "profileMetadata";

    private final CacheManager cacheManager;

    public ProfileTimestampCacheService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    private Cache cache() {
        Cache c = cacheManager.getCache(CACHE_NAME);
        if (c == null) {
            throw new IllegalStateException("Cache '" + CACHE_NAME + "' not configured in CaffeineCacheConfig.");
        }
        return c;
    }

    private String key(String serial, String obis) {
        return serial + "::" + obis;
    }

    public void put(String serial, String obis, LocalDateTime ts) {
        if (ts == null) return;
        cache().put(key(serial, obis), ts);
    }

    public LocalDateTime get(String serial, String obis) {
        return cache().get(key(serial, obis), LocalDateTime.class);
    }

    public void evict(String serial, String obis) {
        cache().evict(key(serial, obis));
    }
}