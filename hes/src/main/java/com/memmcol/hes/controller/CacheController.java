package com.memmcol.hes.controller;

import com.memmcol.hes.cache.CacheEvictionService;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/cache")
public class CacheController {
    private final CacheEvictionService cacheEvictionService;

    @DeleteMapping("/profile-metadata")
    public String evictCache() {
        cacheEvictionService.clearProfileMetadataCache();
        return "profileMetadata cache cleared.";
    }

    @DeleteMapping("/profile-capture-period")
    public String evictProfileCapturePeriodCache() {
        cacheEvictionService.clearProfileCapturePeriodCache();
        return "profileCapturePeriod cache cleared.";
    }

    @DeleteMapping("/last_profile_timestamp")
    public String evictLastProfileTimestampCache() {
        cacheEvictionService.clearLastProfileTimestampCache();
        return "lastProfileTimestamp cache cleared.";
    }
}
