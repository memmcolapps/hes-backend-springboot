package com.memmcol.hes.infrastructure.persistence;

import com.memmcol.hes.application.port.out.ProfileStatePort;
import com.memmcol.hes.cache.EventCodeLookupCacheService;
import com.memmcol.hes.domain.profile.CapturePeriod;
import com.memmcol.hes.domain.profile.ProfileState;
import com.memmcol.hes.domain.profile.ProfileSyncResult;
import com.memmcol.hes.domain.profile.ProfileTimestamp;
import com.memmcol.hes.dto.HouseholdFraudEventDTO;
import com.memmcol.hes.entities.HouseholdFraudEvent;
import com.memmcol.hes.infrastructure.observability.FactTablePersistenceLogging;
import com.memmcol.hes.repository.HouseholdFraudEventCustomRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
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
public class HouseholdFraudEventPersistenceAdapter {

    private static final String TABLE = "household_fraud_event";
    private static final String DOMAIN = "household_fraud_event";

    @PersistenceContext
    private final EntityManager entityManager;
    private final ProfileStatePort statePort;
    private final HouseholdFraudEventCustomRepository customRepository;
    private final EventCodeLookupCacheService lookupCacheService;

    private static final int FLUSH_BATCH = 100;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProfileSyncResult saveBatch(String meterSerial, String profileObis, List<HouseholdFraudEventDTO> dtos) {
        ProfileState st = statePort.loadState(meterSerial, profileObis);
        LocalDateTime previousLast = (st != null && st.lastTimestamp() != null)
                ? st.lastTimestamp().value()
                : null;

        if (dtos == null || dtos.isEmpty()) {
            FactTablePersistenceLogging.logPersistZeroRows(log, DOMAIN, TABLE, meterSerial, "_unknown", profileObis,
                    "NO_INCOMING_ROWS", "No household fraud event rows in batch.", 0);
            return new ProfileSyncResult(0, 0, 0, previousLast, previousLast, previousLast, false);
        }

        int total = dtos.size();
        String meterModel = FactTablePersistenceLogging.firstModel(dtos, HouseholdFraudEventDTO::getMeterModel);
        List<HouseholdFraudEventDTO> filtered = dedupeInMemory(dtos);
        List<HouseholdFraudEventDTO> newRows = deduplicateAgainstDb(meterSerial, filtered);
        int inserted = persistReadings(newRows, profileObis);
        int duplicate = total - inserted;

        LocalDateTime incomingMax = dtos.stream()
                .map(HouseholdFraudEventDTO::getEventTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(previousLast);

        LocalDateTime advanceTo = previousLast == null
                ? incomingMax
                : (previousLast.isAfter(incomingMax) ? previousLast : incomingMax);

        boolean advanced = previousLast == null || (advanceTo != null && advanceTo.isAfter(previousLast));

        if (advanceTo != null) {
            statePort.upsertState(meterSerial, profileObis, new ProfileTimestamp(advanceTo), new CapturePeriod(1));
        }

        FactTablePersistenceLogging.logBatchOutcome(log, DOMAIN, TABLE, meterSerial, meterModel, profileObis,
                inserted, total, duplicate);

        return new ProfileSyncResult(total, inserted, duplicate, previousLast, incomingMax, advanceTo, advanced);
    }

    private int persistReadings(List<HouseholdFraudEventDTO> dtos, String profileObis) {
        Session session = entityManager.unwrap(Session.class);
        int count = 0;
        LocalDateTime now = LocalDateTime.now();

        Integer eventTypeId = lookupCacheService.findEventTypeIdByProfileObis(profileObis)
                .map(Math::toIntExact)
                .orElseGet(() -> {
                    log.warn("No event_type for profile_obis={}, falling back to undefined type", profileObis);
                    return Math.toIntExact(lookupCacheService.getEventTypeIdByObis(profileObis));
                });

        for (HouseholdFraudEventDTO dto : dtos) {
            String eventName = lookupCacheService.resolveHouseholdEventName(
                    profileObis, dto.getEventCode(), dto.getMeterModel());

            HouseholdFraudEvent entity = HouseholdFraudEvent.builder()
                    .meterSerial(dto.getMeterSerial())
                    .meterModel(dto.getMeterModel())
                    .profileObis(profileObis)
                    .eventCode(dto.getEventCode())
                    .eventName(eventName)
                    .eventTypeId(eventTypeId)
                    .eventTime(dto.getEventTime().truncatedTo(ChronoUnit.SECONDS))
                    .totalAbsoluteActiveKwh(dto.getTotalAbsoluteActiveKwh())
                    .balanceKwh(dto.getBalanceKwh())
                    .createdAt(now)
                    .build();
            session.persist(entity);
            count++;
            if (count % FLUSH_BATCH == 0) {
                session.flush();
                session.clear();
            }
        }
        session.flush();
        session.clear();
        return count;
    }

    private List<HouseholdFraudEventDTO> dedupeInMemory(List<HouseholdFraudEventDTO> dtos) {
        return new ArrayList<>(
                dtos.stream()
                        .collect(Collectors.toMap(
                                d -> d.getMeterSerial() + "|" + d.getEventCode() + "|" + d.getEventTime(),
                                Function.identity(),
                                (a, b) -> a,
                                LinkedHashMap::new))
                        .values());
    }

    private List<HouseholdFraudEventDTO> deduplicateAgainstDb(String meterSerial,
                                                            List<HouseholdFraudEventDTO> dtos) {
        if (dtos.isEmpty()) {
            return List.of();
        }
        List<Object[]> existing = customRepository.findExistingEvents(meterSerial, dtos);
        Set<String> existingKeys = existing.stream()
                .map(row -> {
                    String eventCode = String.valueOf(row[0]);
                    LocalDateTime eventTime = (row[1] instanceof Timestamp ts)
                            ? ts.toLocalDateTime()
                            : (LocalDateTime) row[1];
                    return eventCode + "|" + eventTime.truncatedTo(ChronoUnit.SECONDS);
                })
                .collect(Collectors.toSet());

        return dtos.stream()
                .filter(dto -> {
                    String key = dto.getEventCode() + "|" + dto.getEventTime().truncatedTo(ChronoUnit.SECONDS);
                    return !existingKeys.contains(key);
                })
                .toList();
    }
}
