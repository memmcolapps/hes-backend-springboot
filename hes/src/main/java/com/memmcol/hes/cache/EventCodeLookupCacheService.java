package com.memmcol.hes.cache;

import com.memmcol.hes.domain.events.EventCodeLookupMeterModelMatcher;
import com.memmcol.hes.domain.events.ObisCodeNormalizer;
import com.memmcol.hes.entities.EventCodeLookup;
import com.memmcol.hes.entities.EventType;
import com.memmcol.hes.repository.EventCodeLookupRepository;
import com.memmcol.hes.repository.EventTypeRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Caches {@code event_type} (id ↔ obis_code) and {@code event_code_lookup} rows.
 * <p>
 * Household fraud/control {@code event_name} resolves via
 * {@code household.profile_obis + event_code + meter_model} matched to
 * {@code event_type.obis_code} and {@code event_code_lookup} (not a standalone OBIS→id shortcut).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventCodeLookupCacheService {

    private final EventCodeLookupRepository eventCodeLookupRepository;
    private final EventTypeRepository eventTypeRepository;

    private EventType undefinedEventType;

    /** MD/CT {@code event_log}: OBIS → event_type.id (undefined fallback). */
    private final Map<String, Long> obisToEventTypeId = new HashMap<>();

    /** profile_obis (normalized) → event_type.id from {@code event_type.obis_code}. */
    private final Map<String, Long> eventTypeIdByProfileObis = new HashMap<>();

    /** MD/CT: event_type_id + code → name (no meter_model filter). */
    private final Map<String, CachedEventCode> eventCodeByTypeAndCode = new HashMap<>();

    /**
     * Household: profile_obis + code → lookup row (event_name + meter_model scope).
     * Built from event_code_lookup joined to event_type on event_type_id.
     */
    private final Map<String, CachedEventCode> householdEventNameByProfileObisAndCode = new HashMap<>();

    @PostConstruct
    public void loadCacheAtStartup() {
        for (EventType type : eventTypeRepository.findAll()) {
            String obis = type.getObisCode();
            String normalized = ObisCodeNormalizer.normalize(obis);
            obisToEventTypeId.put(obis, type.getId());
            eventTypeIdByProfileObis.put(normalized, type.getId());
        }

        for (EventCodeLookup lookup : eventCodeLookupRepository.findAll()) {
            EventType eventType = lookup.getEventType();
            Long eventTypeId = eventType.getId();
            int code = lookup.getCode();
            CachedEventCode cached = new CachedEventCode(lookup.getEventName(), lookup.getMeterModel());

            eventCodeByTypeAndCode.put(compositeTypeAndCode(eventTypeId, code), cached);

            String profileObis = ObisCodeNormalizer.normalize(eventType.getObisCode());
            householdEventNameByProfileObisAndCode.put(compositeProfileObisAndCode(profileObis, code), cached);
        }

        undefinedEventType = eventTypeRepository.findById(5L)
                .orElseThrow(() -> new IllegalStateException("Undefined EventType (id=5) missing in DB"));

        log.info("✅ EventCodeLookup cache: {} event types, {} codes, {} household name keys",
                eventTypeIdByProfileObis.size(), eventCodeByTypeAndCode.size(),
                householdEventNameByProfileObisAndCode.size());
    }

    /**
     * MD/CT tier only: resolves event_type.id with undefined-event fallback.
     */
    public Long getEventTypeIdByObis(String obisCode) {
        return obisToEventTypeId.getOrDefault(obisCode, undefinedEventType.getId());
    }

    /**
     * {@code event_type.id} for a profile OBIS via {@code event_type.obis_code} (no undefined fallback).
     */
    public Optional<Long> findEventTypeIdByProfileObis(String profileObis) {
        return Optional.ofNullable(eventTypeIdByProfileObis.get(ObisCodeNormalizer.normalize(profileObis)));
    }

    /**
     * MD/CT {@code event_log} event name (event_type_id + code, all meter models).
     */
    public Optional<String> getEventName(Long eventTypeId, int eventCode) {
        return getEventNameForCached(eventCodeByTypeAndCode.get(compositeTypeAndCode(eventTypeId, eventCode)), null);
    }

    /**
     * Household fraud/control: match {@code profile_obis}, {@code event_code}, {@code meter_model}
     * to {@code event_type.obis_code} + {@code event_code_lookup}.
     */
    public String resolveHouseholdEventName(String profileObis, int eventCode, String meterModel) {
        String key = compositeProfileObisAndCode(ObisCodeNormalizer.normalize(profileObis), eventCode);
        return getEventNameForCached(householdEventNameByProfileObisAndCode.get(key), meterModel)
                .orElse("Undefined Event");
    }

    private Optional<String> getEventNameForCached(CachedEventCode cached, String meterModel) {
        if (cached == null) {
            return Optional.empty();
        }
        if (!EventCodeLookupMeterModelMatcher.applies(cached.meterModels(), meterModel)) {
            return Optional.empty();
        }
        return Optional.ofNullable(cached.eventName());
    }

    private static String compositeTypeAndCode(Long eventTypeId, int eventCode) {
        return eventTypeId + "_" + eventCode;
    }

    private static String compositeProfileObisAndCode(String profileObis, int eventCode) {
        return profileObis + "_" + eventCode;
    }

    public void refreshCache() {
        obisToEventTypeId.clear();
        eventTypeIdByProfileObis.clear();
        eventCodeByTypeAndCode.clear();
        householdEventNameByProfileObisAndCode.clear();
        loadCacheAtStartup();
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledRefresh() {
        refreshCache();
    }

    private record CachedEventCode(String eventName, String meterModels) {
    }
}
