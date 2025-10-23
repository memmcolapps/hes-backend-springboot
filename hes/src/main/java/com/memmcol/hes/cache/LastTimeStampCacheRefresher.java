package com.memmcol.hes.cache;

import com.memmcol.hes.trackByTimestamp.MeterProfileState;
import com.memmcol.hes.trackByTimestamp.MeterProfileStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class LastTimeStampCacheRefresher {
    private final MeterProfileStateRepository stateRepo;
    private final CacheManager cacheManager;

    private static final String CACHE_NAME = "lastProfileTimestamp";
    private static final String CACHE_PREFIX = "lastTs::";

    /**
     * Scheduler: refreshes cache every 6 hours,
     * removes stale entries, and repopulates active ones.
     */
    @Scheduled(fixedRate = 6 * 60 * 60 * 1000) // every 6 hours
    @Transactional(readOnly = true)
    public void refreshLastProfileTimestampCache() {
        log.info("⏳ Starting scheduled refresh of '{}' cache...", CACHE_NAME);

        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) {
            log.warn("Cache '{}' not found — refresh skipped", CACHE_NAME);
            return;
        }

        // Step 1️⃣: Load all active meter profile states
        List<MeterProfileState> states = stateRepo.findAll();
        Map<String, LocalDateTime> dbMap = new HashMap<>();

        for (MeterProfileState s : states) {
            if (s.getLastTimestamp() != null) {
                String key = CACHE_PREFIX + s.getMeterSerial() + "::" + s.getProfileObis();
                dbMap.put(key, s.getLastTimestamp());
            }
        }

        // Step 2️⃣: Refresh or remove cache entries
        com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache =
                (com.github.benmanes.caffeine.cache.Cache<Object, Object>)
                        cache.getNativeCache();

        Set<Object> currentKeys = nativeCache.asMap().keySet();
        int refreshed = 0;
        int removed = 0;

        // 2a. Remove cache entries not in DB (invalid/stale)
        for (Object key : currentKeys) {
            if (!dbMap.containsKey(key)) {
                nativeCache.invalidate(key);
                removed++;
            }
        }

        // 2b. Refresh cache with current DB values
        for (Map.Entry<String, LocalDateTime> entry : dbMap.entrySet()) {
            nativeCache.put(entry.getKey(), entry.getValue());
            refreshed++;
        }

        log.info("✅ '{}' cache refreshed — {} entries updated, {} stale removed",
                CACHE_NAME, refreshed, removed);
    }
}
