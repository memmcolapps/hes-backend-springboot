package com.memmcol.hes.infrastructure.persistence;

import com.memmcol.hes.application.port.out.ProfileStatePort;
import com.memmcol.hes.cache.EventCodeLookupCacheService;
import com.memmcol.hes.domain.profile.CapturePeriod;
import com.memmcol.hes.domain.profile.ProfileState;
import com.memmcol.hes.domain.profile.ProfileSyncResult;
import com.memmcol.hes.domain.profile.ProfileTimestamp;
import com.memmcol.hes.entities.EventCodeLookup;
import com.memmcol.hes.dto.EventLogDTO;
import com.memmcol.hes.entities.EventLog;
import com.memmcol.hes.entities.EventType;
import com.memmcol.hes.repository.EventCodeLookupRepository;
import com.memmcol.hes.repository.EventLogCustomRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Slf4j
public class EventLogPersistenceAdapter {

    @PersistenceContext
    private final EntityManager entityManager;
    private final EventCodeLookupCacheService lookupCacheService;
    private final EventCodeLookupRepository eventCodeLookupRepository;
    private final ProfileStatePort statePort;
    private static final int FLUSH_BATCH = 100;
    private final EventLogCustomRepository customRepository;

    /*TODO:
     *  1.  Add to other profiles saveBatch method: @Transactional(propagation = Propagation.REQUIRES_NEW)
     * */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProfileSyncResult saveBatch(String meterSerial,
                                       String profileOBIS, List<EventLogDTO> dtos) {
        ProfileState st = statePort.loadState(meterSerial, profileOBIS); // or null if you dropped obis
        LocalDateTime previousLast = (st != null && st.lastTimestamp() != null)
                ? st.lastTimestamp().value()
                : null;

        if (dtos == null || dtos.isEmpty()) {
            log.info("saveBatchAndAdvanceCursor: no rows for meter={}", meterSerial);
            return new ProfileSyncResult(
                    0, 0, 0, previousLast, previousLast, previousLast, false
            );
        }

        int total = dtos.size();
        //Deduplication by ****
        List<EventLogDTO> filteredRows = dedupeInMemory(dtos);
        List<EventLogDTO> filteredRows2 = deduplicateEventLogs(meterSerial, filteredRows);
        //Insert filtered rows
        int inserted = persistReadings(filteredRows2, profileOBIS);
        int duplicate = total - inserted;

        LocalDateTime incomingMax = dtos.stream()
                .map(EventLogDTO::getEventTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(previousLast);

        LocalDateTime advanceTo = previousLast == null
                ? incomingMax
                : (previousLast.isAfter(incomingMax) ? previousLast : incomingMax);

        boolean advanced = previousLast == null || advanceTo.isAfter(previousLast);

        if (advanceTo != null) {
            statePort.upsertState(meterSerial, profileOBIS, new ProfileTimestamp(advanceTo).plus(new CapturePeriod(1)), new CapturePeriod(1));
        }

        log.info("Batch persisted meter={} total={} inserted={} dup={} start={} end={} advanceTo={}",
                meterSerial, total, inserted, duplicate, previousLast, incomingMax, advanceTo);

        return new ProfileSyncResult(total, inserted, duplicate, previousLast, incomingMax, advanceTo, advanced);
    }

    private int persistReadings(List<EventLogDTO> dtos, String profileOBIS) {
        Session session = entityManager.unwrap(Session.class);
        int count = 0;

        for (EventLogDTO dto : dtos) {
            // 1. Get EventTypeId from OBIS
            Long eventTypeId = lookupCacheService.getEventTypeIdByObis(profileOBIS);

            // 2. Get EventName from cache (fallback → "Undefined Event")
            String eventName = lookupCacheService.getEventName(eventTypeId, dto.getEventCode())
                    .orElse("Undefined Event");

            // 4️⃣ Map DTO to Entity
            EventLog logEntry = EventLog.builder()
                    .meterSerial(dto.getMeterSerial())
                    .eventType(eventTypeId)
                    .eventCode(dto.getEventCode())
                    .eventTime(dto.getEventTime().truncatedTo(ChronoUnit.SECONDS))
                    .phase(dto.getPhase())
                    .eventName(eventName)
                    .build();

            // 5️⃣ Persist entity
            session.persist(logEntry);
            count++;

            if (count % FLUSH_BATCH == 0) {
                session.flush();
                session.clear();
            }
        }
        session.flush();
        session.clear();

        log.info("💾 Saved {} readings to DB.", count);
        return count;
    }

    private List<EventLogDTO> dedupeInMemory(List<EventLogDTO> dtos) {
        return new ArrayList<>(
                dtos.stream()
                        .collect(Collectors.toMap(
                                d -> d.getMeterSerial() + "|" + d.getEventCode() + "|" + d.getEventTime(),
                                Function.identity(),
                                (a, b) -> a,
                                LinkedHashMap::new
                        ))
                        .values()
        );
    }

    public List<EventLogDTO> deduplicateEventLogs(String meterSerial, List<EventLogDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            log.info("⚠ No event logs provided for deduplication.");
            return List.of();
        }

        // Fetch existing rows
        List<Object[]> existing = customRepository.findExistingEvents(meterSerial, dtos);
        Set<String> existingKeys = existing.stream()
                .map(row -> {
                    String eventCode = String.valueOf(row[0]);
                    LocalDateTime eventTime = (row[1] instanceof Timestamp ts) ? ts.toLocalDateTime() : (LocalDateTime) row[1];
                    return eventCode + "|" + eventTime.truncatedTo(ChronoUnit.SECONDS);
                })
                .collect(Collectors.toSet());
        // Filter only new ones
        List<EventLogDTO> filtered = dtos.stream()
                .filter(dto -> {
                    String key = dto.getEventCode() + "|" + dto.getEventTime().truncatedTo(ChronoUnit.SECONDS);
                    return !existingKeys.contains(key);
                })
                .toList();

        if (filtered.isEmpty()) {
            log.info("✅ No new events to save — all already exist.");
            return List.of();
        }

        log.info("📌 {} new events will be saved for meter {}", filtered.size(), meterSerial);
        return filtered;
    }

}
