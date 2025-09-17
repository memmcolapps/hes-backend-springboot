package com.memmcol.hes.cache;

import com.memmcol.hes.entities.EventCodeLookup;
import com.memmcol.hes.entities.EventType;
import com.memmcol.hes.model.DlmsObisMapper;
import com.memmcol.hes.repository.EventCodeLookupRepository;
import com.memmcol.hes.repository.EventTypeRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventCodeLookupCacheService {

    private final EventCodeLookupRepository eventCodeLookupRepository;
    private final EventTypeRepository eventTypeRepository;
    private final CacheManager cacheManager;

    private EventType undefinedEventType; // cached reference

    // Cache maps
    private final Map<String, Long> obisToEventTypeId = new HashMap<>();
    private final Map<String, String> eventCodeMap = new HashMap<>();

    @PostConstruct
    public void loadCacheAtStartup() {
        // Load event types
        for (EventType type : eventTypeRepository.findAll()) {
            obisToEventTypeId.put(type.getObisCode(), type.getId());
        }

        // Load event code lookups
        for (EventCodeLookup lookup : eventCodeLookupRepository.findAll()) {
            String key = compositeKey(lookup.getEventType().getId(), lookup.getCode());
            eventCodeMap.put(key, lookup.getEventName());
        }

        // preload Undefined EventType (id=5)
        undefinedEventType = eventTypeRepository.findById(5L)
                .orElseThrow(() -> new IllegalStateException("Undefined EventType (id=5) missing in DB"));

        log.info("✅ EventCodeLookup cache initialized with Loaded event types {} and event codes {} entries",
                obisToEventTypeId.size(),  eventCodeMap.size());
    }

    public Long getEventTypeIdByObis(String obisCode) {
        return obisToEventTypeId.getOrDefault(obisCode, undefinedEventType.getId());
    }

    public Optional<String> getEventName(Long eventTypeId, int eventCode) {
        String key = compositeKey(eventTypeId, eventCode);
        return Optional.ofNullable(eventCodeMap.get(key));
    }

    private String compositeKey(Long eventTypeId, int eventCode) {
        return eventTypeId + "_" + eventCode;
    }

    // ✅ Refresh cache
    public void refreshCache() {
        obisToEventTypeId.clear();
        eventCodeMap.clear();
        loadCacheAtStartup();
    }

    @Scheduled(cron = "0 0 2 * * ?") // daily at 2 AM
    public void scheduledRefresh() {
        refreshCache();
    }
}