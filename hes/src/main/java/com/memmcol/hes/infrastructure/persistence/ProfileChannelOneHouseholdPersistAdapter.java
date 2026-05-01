package com.memmcol.hes.infrastructure.persistence;

import com.memmcol.hes.application.port.out.ProfileStatePort;
import com.memmcol.hes.domain.profile.CapturePeriod;
import com.memmcol.hes.domain.profile.ProfileState;
import com.memmcol.hes.domain.profile.ProfileSyncResult;
import com.memmcol.hes.domain.profile.ProfileTimestamp;
import com.memmcol.hes.dto.ProfileChannelOneHouseholdDTO;
import com.memmcol.hes.entities.ProfileChannelOneHousehold;
import com.memmcol.hes.entities.ProfileChannelOneHouseholdToEntity;
import com.memmcol.hes.entities.ProfileChannelOneId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Repository;
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

@Repository
@Slf4j
public class ProfileChannelOneHouseholdPersistAdapter {
    @PersistenceContext
    private EntityManager entityManager;
    private final ProfileStatePort statePort;
    private static final int FLUSH_BATCH = 100;

    public ProfileChannelOneHouseholdPersistAdapter(ProfileStatePort statePort) {
        this.statePort = statePort;
    }

    public List<LocalDateTime> findExistingTimestamps(String serial, List<LocalDateTime> timestamps) {
        if (timestamps == null || timestamps.isEmpty()) {
            return List.of();
        }
        TypedQuery<LocalDateTime> query = entityManager.createQuery(
                "SELECT r.entryTimestamp " +
                        "FROM ProfileChannelOneHousehold r " +
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
                                                       String profileOBIS,
                                                       List<ProfileChannelOneHouseholdDTO> readings,
                                                       CapturePeriod capturePeriodSeconds) {
        ProfileState st = statePort.loadState(meterSerial, profileOBIS);
        LocalDateTime previousLast = (st != null && st.lastTimestamp() != null)
                ? st.lastTimestamp().value()
                : null;

        if (readings == null || readings.isEmpty()) {
            log.info("saveBatchAndAdvanceCursor(hh ch1): no rows for meter={}", meterSerial);
            return new ProfileSyncResult(0, 0, 0, previousLast, previousLast, previousLast, false);
        }

        int total = readings.size();
        List<ProfileChannelOneHouseholdDTO> filteredRows = deduplicate(meterSerial, readings);
        int inserted = persistReadingsByMonth(filteredRows);
        int duplicate = total - inserted;

        LocalDateTime incomingMax = readings.stream()
                .map(ProfileChannelOneHouseholdDTO::getEntryTimestamp)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(previousLast);

        LocalDateTime advanceTo = previousLast == null
                ? incomingMax
                : (previousLast.isAfter(incomingMax) ? previousLast : incomingMax);

        boolean advanced = previousLast == null || (advanceTo != null && advanceTo.isAfter(previousLast));

        if (advanceTo != null) {
            statePort.upsertState(meterSerial, profileOBIS, new ProfileTimestamp(advanceTo), capturePeriodSeconds);
        }

        log.info("Batch persisted(hh ch1) meter={} total={} inserted={} dup={} start={} end={} advanceTo={}",
                meterSerial, total, inserted, duplicate, previousLast, incomingMax, advanceTo);

        return new ProfileSyncResult(total, inserted, duplicate, previousLast, incomingMax, advanceTo, advanced);
    }

    private List<ProfileChannelOneHouseholdDTO> deduplicate(String meterSerial, List<ProfileChannelOneHouseholdDTO> readings) {
        if (readings == null || readings.isEmpty()) {
            return List.of();
        }
        List<LocalDateTime> incomingTimestamps = readings.stream()
                .map(ProfileChannelOneHouseholdDTO::getEntryTimestamp)
                .collect(Collectors.toList());
        List<LocalDateTime> existingTimestamps = findExistingTimestamps(meterSerial, incomingTimestamps);
        return readings.stream()
                .filter(dto -> dto.getEntryTimestamp() != null && !existingTimestamps.contains(dto.getEntryTimestamp()))
                .collect(Collectors.toList());
    }

    public int persistReadingsByMonth(List<ProfileChannelOneHouseholdDTO> filteredRows) {
        Session session = entityManager.unwrap(Session.class);
        final AtomicInteger saved = new AtomicInteger();

        filteredRows.stream()
                .filter(dto -> dto.getEntryTimestamp() != null)
                .forEach(dto -> {
            ProfileChannelOneHousehold entity = ProfileChannelOneHouseholdToEntity.toEntity(dto);
            ProfileChannelOneId id = new ProfileChannelOneId(entity.getMeterSerial(), entity.getEntryTimestamp());
            ProfileChannelOneHousehold existing = session.get(ProfileChannelOneHousehold.class, id);

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

    public void createPartitionsIfMissing(List<ProfileChannelOneHouseholdDTO> filteredRows) {
        Session session = entityManager.unwrap(Session.class);

        Set<String> months = filteredRows.stream()
                .map(ProfileChannelOneHouseholdDTO::getEntryTimestamp)
                .filter(Objects::nonNull)
                .map(ts -> ts.format(DateTimeFormatter.ofPattern("yyyyMM")))
                .collect(Collectors.toSet());

        months.forEach(month -> {
            String partitionName = "profile_channel_one_hh_" + month;
            String startDate = month + "01";
            String endDate = YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyyMM"))
                    .plusMonths(1)
                    .atDay(1)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE);

            String sql = String.format(
                    "CREATE TABLE IF NOT EXISTS %s PARTITION OF profile_channel_one_hh " +
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
}

