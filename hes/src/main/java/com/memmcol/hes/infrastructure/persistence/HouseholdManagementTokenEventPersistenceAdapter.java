package com.memmcol.hes.infrastructure.persistence;

import com.memmcol.hes.application.port.out.ProfileStatePort;
import com.memmcol.hes.domain.profile.CapturePeriod;
import com.memmcol.hes.domain.profile.ProfileState;
import com.memmcol.hes.domain.profile.ProfileSyncResult;
import com.memmcol.hes.domain.profile.ProfileTimestamp;
import com.memmcol.hes.dto.HouseholdManagementTokenEventDTO;
import com.memmcol.hes.entities.HouseholdManagementTokenEvent;
import com.memmcol.hes.infrastructure.observability.FactTablePersistenceLogging;
import com.memmcol.hes.repository.HouseholdManagementTokenEventCustomRepository;
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
public class HouseholdManagementTokenEventPersistenceAdapter {

    private static final String TABLE = "household_management_token_event";
    private static final String DOMAIN = "household_management_token_event";

    @PersistenceContext
    private final EntityManager entityManager;
    private final ProfileStatePort statePort;
    private final HouseholdManagementTokenEventCustomRepository customRepository;

    private static final int FLUSH_BATCH = 100;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProfileSyncResult saveBatch(String meterSerial, String profileObis, List<HouseholdManagementTokenEventDTO> dtos) {
        ProfileState st = statePort.loadState(meterSerial, profileObis);
        LocalDateTime previousLast = (st != null && st.lastTimestamp() != null)
                ? st.lastTimestamp().value()
                : null;

        if (dtos == null || dtos.isEmpty()) {
            FactTablePersistenceLogging.logPersistZeroRows(log, DOMAIN, TABLE, meterSerial, "_unknown", profileObis,
                    "NO_INCOMING_ROWS", "No household management token rows in batch.", 0);
            return new ProfileSyncResult(0, 0, 0, previousLast, previousLast, previousLast, false);
        }

        int total = dtos.size();
        String meterModel = FactTablePersistenceLogging.firstModel(dtos, HouseholdManagementTokenEventDTO::getMeterModel);
        List<HouseholdManagementTokenEventDTO> filtered = dedupeInMemory(dtos);
        List<HouseholdManagementTokenEventDTO> newRows = deduplicateAgainstDb(meterSerial, filtered);
        int inserted = persistReadings(newRows, profileObis);
        int duplicate = total - inserted;

        LocalDateTime incomingMax = dtos.stream()
                .map(HouseholdManagementTokenEventDTO::getEventTime)
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

    private int persistReadings(List<HouseholdManagementTokenEventDTO> dtos, String profileObis) {
        Session session = entityManager.unwrap(Session.class);
        int count = 0;
        LocalDateTime now = LocalDateTime.now();

        for (HouseholdManagementTokenEventDTO dto : dtos) {
            HouseholdManagementTokenEvent entity = HouseholdManagementTokenEvent.builder()
                    .meterSerial(dto.getMeterSerial())
                    .meterModel(dto.getMeterModel())
                    .profileObis(profileObis)
                    .eventCode(dto.getEventCode())
                    .eventTime(dto.getEventTime().truncatedTo(ChronoUnit.SECONDS))
                    .manageTokenType(dto.getManageTokenType())
                    .manageToken(dto.getManageToken())
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

    private List<HouseholdManagementTokenEventDTO> dedupeInMemory(List<HouseholdManagementTokenEventDTO> dtos) {
        return new ArrayList<>(
                dtos.stream()
                        .collect(Collectors.toMap(
                                d -> d.getMeterSerial() + "|" + d.getEventCode() + "|" + d.getEventTime(),
                                Function.identity(),
                                (a, b) -> a,
                                LinkedHashMap::new))
                        .values());
    }

    private List<HouseholdManagementTokenEventDTO> deduplicateAgainstDb(String meterSerial,
                                                                       List<HouseholdManagementTokenEventDTO> dtos) {
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
