package com.memmcol.hes.infrastructure.persistence;

import com.memmcol.hes.application.port.out.ProfileStatePort;
import com.memmcol.hes.domain.profile.CapturePeriod;
import com.memmcol.hes.domain.profile.ProfileState;
import com.memmcol.hes.domain.profile.ProfileSyncResult;
import com.memmcol.hes.domain.profile.ProfileTimestamp;
import com.memmcol.hes.dto.ProfileChannelTwoHouseholdDTO;
import com.memmcol.hes.entities.ProfileChannelTwoHousehold;
import com.memmcol.hes.entities.ProfileChannelTwoHouseholdId;
import com.memmcol.hes.entities.ProfileChannelTwoHouseholdToEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
@Service
public class ProfileChannelTwoHouseholdPersistenceAdapter {
    @PersistenceContext
    private final EntityManager em;
    private static final int FLUSH_BATCH = 500;
    private final ProfileStatePort statePort;

    public void createPartitionsIfMissing(List<ProfileChannelTwoHouseholdDTO> filteredRows) {
        Session session = em.unwrap(Session.class);

        Set<String> months = filteredRows.stream()
                .map(ProfileChannelTwoHouseholdDTO::getEntryTimestamp)
                .filter(Objects::nonNull)
                .map(ts -> ts.format(DateTimeFormatter.ofPattern("yyyyMM")))
                .collect(Collectors.toSet());

        months.forEach(month -> {
            String partitionName = "profile_channel_two_hh_" + month;
            String startDate = month + "01";
            String endDate = YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyyMM"))
                    .plusMonths(1)
                    .atDay(1)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE);

            String sql = String.format(
                    "CREATE TABLE IF NOT EXISTS %s PARTITION OF profile_channel_two_hh " +
                            "FOR VALUES FROM ('%s') TO ('%s');",
                    partitionName, startDate, endDate
            );

            session.doWork(connection -> {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute(sql);
                    log.info("🆕 Partition {} created.", partitionName);
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("exists")) {
                        log.debug("Partition {} already exists, skipping.", partitionName);
                    } else {
                        log.error("Error creating partition {}: {}", partitionName, e.getMessage());
                    }
                }
            });
        });
    }

    public List<LocalDateTime> findExistingTimestamps(String serial, List<LocalDateTime> timestamps) {
        if (timestamps == null || timestamps.isEmpty()) {
            return List.of();
        }
        TypedQuery<LocalDateTime> query = em.createQuery(
                "SELECT r.entryTimestamp " +
                        "FROM ProfileChannelTwoHousehold r " +
                        "WHERE r.meterSerial = :serial " +
                        "AND r.entryTimestamp IN :timestamps",
                LocalDateTime.class
        );
        query.setParameter("serial", serial);
        query.setParameter("timestamps", timestamps);
        return query.getResultList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProfileSyncResult saveBatchAndAdvanceCursor(String meterSerial,
                                                       String obis,
                                                       List<ProfileChannelTwoHouseholdDTO> readings,
                                                       CapturePeriod capturePeriodSeconds) {
        ProfileState st = statePort.loadState(meterSerial, obis);
        LocalDateTime previousLast = (st != null && st.lastTimestamp() != null)
                ? st.lastTimestamp().value()
                : null;

        if (readings == null || readings.isEmpty()) {
            return new ProfileSyncResult(0, 0, 0, previousLast, previousLast, previousLast, false);
        }

        int total = readings.size();
        List<ProfileChannelTwoHouseholdDTO> filteredRows = deduplicate(meterSerial, readings);
        int inserted = persistReadingsByMonth(filteredRows);
        int duplicate = total - inserted;

        LocalDateTime incomingMax = readings.stream()
                .map(ProfileChannelTwoHouseholdDTO::getEntryTimestamp)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(previousLast);

        LocalDateTime advanceTo = previousLast == null
                ? incomingMax
                : (previousLast.isAfter(incomingMax) ? previousLast : incomingMax);

        boolean advanced = previousLast == null || (advanceTo != null && advanceTo.isAfter(previousLast));

        if (advanceTo != null) {
            statePort.upsertState(meterSerial, obis, new ProfileTimestamp(advanceTo), capturePeriodSeconds);
        }

        return new ProfileSyncResult(total, inserted, duplicate, previousLast, incomingMax, advanceTo, advanced);
    }

    private List<ProfileChannelTwoHouseholdDTO> deduplicate(String meterSerial, List<ProfileChannelTwoHouseholdDTO> readings) {
        List<LocalDateTime> incomingTimestamps = readings.stream()
                .map(ProfileChannelTwoHouseholdDTO::getEntryTimestamp)
                .collect(Collectors.toList());
        List<LocalDateTime> existingTimestamps = findExistingTimestamps(meterSerial, incomingTimestamps);
        return readings.stream()
                .filter(dto -> dto.getEntryTimestamp() != null && !existingTimestamps.contains(dto.getEntryTimestamp()))
                .collect(Collectors.toList());
    }

    public int persistReadingsByMonth(List<ProfileChannelTwoHouseholdDTO> filteredRows) {
        Session session = em.unwrap(Session.class);
        final AtomicInteger saved = new AtomicInteger();

        filteredRows.stream()
                .filter(dto -> dto.getEntryTimestamp() != null)
                .forEach(dto -> {
            ProfileChannelTwoHousehold entity = ProfileChannelTwoHouseholdToEntity.toEntity(dto);
            ProfileChannelTwoHouseholdId id = new ProfileChannelTwoHouseholdId(entity.getMeterSerial(), entity.getEntryTimestamp());
            ProfileChannelTwoHousehold existing = session.get(ProfileChannelTwoHousehold.class, id);

            if (existing == null) {
                session.persist(entity);
            } else {
                session.merge(entity);
            }

            if (saved.incrementAndGet() % FLUSH_BATCH == 0) {
                session.flush();
                session.clear();
            }
        });

        session.flush();
        session.clear();
        return saved.get();
    }
}

