package com.memmcol.hes.cache;

import com.memmcol.hes.entities.EventCodeLookup;
import com.memmcol.hes.entities.EventType;
import com.memmcol.hes.repository.EventCodeLookupRepository;
import com.memmcol.hes.repository.EventTypeRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventCodeLookupCacheService {

    private final EventCodeLookupRepository eventCodeLookupRepository;
    private final EventTypeRepository eventTypeRepository;
    private final CacheManager cacheManager;

    private EventType undefinedEventType; // cached reference

    @PostConstruct
    public void loadCacheAtStartup() {
        Cache cache = cacheManager.getCache("eventCodeLookup");
        if (cache != null) {
            List<EventCodeLookup> allLookups = eventCodeLookupRepository.findAll();

            // Map code -> EventType
            Map<Integer, EventType> typeMap = allLookups.stream()
                    .collect(Collectors.toMap(EventCodeLookup::getCode, EventCodeLookup::getEventType));

            // Map code -> description
            Map<Integer, String> descMap = allLookups.stream()
                    .collect(Collectors.toMap(EventCodeLookup::getCode, EventCodeLookup::getDescription));

            cache.put("typeMap", typeMap);
            cache.put("descMap", descMap);

            log.info("✅ EventCodeLookup cache initialized with {} entries", typeMap.size());
        }

        // preload Undefined EventType (id=5)
        undefinedEventType = eventTypeRepository.findById(5L)
                .orElseThrow(() -> new IllegalStateException("Undefined EventType (id=5) missing in DB"));
    }

    @SuppressWarnings("unchecked")
    public Map<Integer, EventType> getTypeMap() {
        Cache cache = cacheManager.getCache("eventCodeLookup");
        if (cache != null) {
            return cache.get("typeMap", Map.class);
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    public Map<Integer, String> getDescriptionMap() {
        Cache cache = cacheManager.getCache("eventCodeLookup");
        if (cache != null) {
            return cache.get("descMap", Map.class);
        }
        return Collections.emptyMap();
    }

    public EventType getEventTypeByCode(Integer code) {
        EventType type = getTypeMap().get(code);
        if (type == null) {
            log.warn("⚠️ Undefined event code encountered: {}", code);
            return undefinedEventType;
        }
        return type;
    }

    public String getDescriptionByCode(Integer code) {
        return getDescriptionMap().getOrDefault(code, "Undefined Event");
    }

    // Optional: refresh cache manually or via scheduled task
    public void refreshCache() {
        log.info("♻️ Refreshing EventCodeLookup cache...");
        List<EventCodeLookup> allLookups = eventCodeLookupRepository.findAll();

        Map<Integer, EventType> typeMap = allLookups.stream()
                .collect(Collectors.toMap(EventCodeLookup::getCode, EventCodeLookup::getEventType));

        Map<Integer, String> descMap = allLookups.stream()
                .collect(Collectors.toMap(EventCodeLookup::getCode, EventCodeLookup::getDescription));

        Cache cache = cacheManager.getCache("eventCodeLookup");
        if (cache != null) {
            cache.put("typeMap", typeMap);
            cache.put("descMap", descMap);
        }

        log.info("✅ EventCodeLookup cache refreshed with {} entries", typeMap.size());
    }

    @Scheduled(cron = "0 0 2 * * ?") // daily at 2 AM
    public void scheduledRefresh() {
        refreshCache();
    }
}