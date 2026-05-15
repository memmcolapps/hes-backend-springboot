package com.memmcol.hes.infrastructure.persistence;

import com.memmcol.hes.application.port.out.ProfileStatePort;
import com.memmcol.hes.domain.profile.*;
import com.memmcol.hes.dto.ProfileChannelTwoDTO;
import com.memmcol.hes.entities.*;
import com.memmcol.hes.infrastructure.observability.FactTablePersistenceLogging;
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
public class ProfileChannelTwoPersistenceAdapter {
    @PersistenceContext
    private final EntityManager em;
    private static final int FLUSH_BATCH = 500;
    private final ProfileStatePort statePort;

    /**
     * Creates monthly partition tables for any missing months found in the DTO list.
     */
    public void createPartitionsIfMissing(List<ProfileChannelTwoDTO> filteredRows) {
        Session session = em.unwrap(Session.class);

        // 1. Extract unique months (YYYYMM format)
        Set<String> months = filteredRows.stream()
                .map(dto -> dto.getEntryTimestamp().format(DateTimeFormatter.ofPattern("yyyyMM")))
                .collect(Collectors.toSet());

        // 2. Pre-create missing partitions
        months.forEach(month -> {
            String partitionName = "profile_channel_two_" + month;
            String startDate = month + "01";
            String endDate = YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyyMM"))
                    .plusMonths(1)
                    .atDay(1)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE);

            String sql = String.format(
                    "CREATE TABLE IF NOT EXISTS %s PARTITION OF profile_channel_two " +
                            "FOR VALUES FROM ('%s') TO ('%s');",
                    partitionName, startDate, endDate
            );

            session.doWork(connection -> {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute(sql);
                    log.info("🆕 Partition {} created.", partitionName);
                } catch (Exception e) {
                    if (e.getMessage().contains("exists")) {
                        log.debug("Partition {} already exists, skipping.", partitionName);
                    } else {
                        log.error("Error creating partition {}: {}", partitionName, e.getMessage());
                    }
                }
            });
        });
    }

    public ProfileTimestamp findLatestTimestamp(String meterSerial, String model) {
        List<LocalDateTime> list = em.createQuery("""
                        select r.entryTimestamp from ProfileChannel2Reading r
                         where r.meterSerial=:ms and r.modelNumber=:mn
                         order by r.entryTimestamp desc
                         """, java.time.LocalDateTime.class)
                .setParameter("ms", meterSerial)
                .setParameter("mn", model)
                .setMaxResults(1)
                .getResultList();
        return list.isEmpty() ? null : new ProfileTimestamp(list.get(0));
    }

    /**
     * Find already existing timestamps for a given meter serial
     */
    public List<LocalDateTime> findExistingTimestamps(String serial, List<LocalDateTime> timestamps) {
        if (timestamps == null || timestamps.isEmpty()) {
            return List.of();
        }
        TypedQuery<LocalDateTime> query = em.createQuery(
                "SELECT r.entryTimestamp " +
                        "FROM ProfileChannelTwo r " +
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
                                                       String meterModel,
                                                       String obis,
                                                       List<ProfileChannelTwoDTO> readings,
                                                       CapturePeriod capturePeriodSeconds,
                                                       ProfileMetadataResult metadataResult) {
        final String table = "profile_channel_two";
        final String domain = "profile";
        try {
            ProfileState st = statePort.loadState(meterSerial, obis);
            LocalDateTime previousLast = (st != null && st.lastTimestamp() != null)
                    ? st.lastTimestamp().value()
                    : null;

            if (readings == null || readings.isEmpty()) {
                FactTablePersistenceLogging.logPersistZeroRows(log, domain, table, meterSerial, FactTablePersistenceLogging.nz(meterModel), obis,
                        "NO_INCOMING_ROWS", "readings null or empty before persist", 0);
                return new ProfileSyncResult(0, 0, 0, previousLast, previousLast, previousLast, false);
            }

            String resolvedModel = FactTablePersistenceLogging.nz(meterModel);
            if ("_unknown".equals(resolvedModel)) {
                resolvedModel = FactTablePersistenceLogging.firstModel(readings, ProfileChannelTwoDTO::getModelNumber);
            }

            int total = readings.size();
            List<ProfileChannelTwoDTO> filteredRows = deduplicate(meterSerial, readings);
            int inserted = persistReadingsByMonth(filteredRows);
            int duplicate = total - inserted;

            LocalDateTime incomingMax = readings.stream()
                    .map(ProfileChannelTwoDTO::getEntryTimestamp)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(previousLast);

            LocalDateTime advanceTo = previousLast == null
                    ? incomingMax
                    : (previousLast.isAfter(incomingMax) ? previousLast : incomingMax);

            boolean advanced = previousLast == null || advanceTo.isAfter(previousLast);

            if (advanceTo != null) {
                statePort.upsertState(meterSerial, obis, new ProfileTimestamp(advanceTo), capturePeriodSeconds);
            }

            FactTablePersistenceLogging.logBatchOutcome(log, domain, table, meterSerial, resolvedModel, obis, inserted, total, duplicate);

            return new ProfileSyncResult(total, inserted, duplicate, previousLast, incomingMax, advanceTo, advanced);
        } catch (Exception e) {
            String mm = FactTablePersistenceLogging.nz(meterModel);
            if ("_unknown".equals(mm) && readings != null && !readings.isEmpty()) {
                mm = FactTablePersistenceLogging.firstModel(readings, ProfileChannelTwoDTO::getModelNumber);
            }
            FactTablePersistenceLogging.logPersistFailure(log, domain, table, meterSerial, mm, obis, e);
            throw e;
        }
    }

    /**
     * Deduplicate by checking which timestamps already exist in DB
     */
    private List<ProfileChannelTwoDTO> deduplicate(String meterSerial, List<ProfileChannelTwoDTO> readings) {
        if (readings == null || readings.isEmpty()) {
            log.info("⚠ No readings provided for deduplication.");
            return List.of();
        }

        // Extract timestamps from incoming readings
        List<LocalDateTime> incomingTimestamps = readings.stream()
                .map(ProfileChannelTwoDTO::getEntryTimestamp)
                .collect(Collectors.toList());

        // Query DB for existing timestamps
        List<LocalDateTime> existingTimestamps = findExistingTimestamps(meterSerial, incomingTimestamps);

        // Filter only new readings
        List<ProfileChannelTwoDTO> filteredRows = readings.stream()
                .filter(dto -> !existingTimestamps.contains(dto.getEntryTimestamp()))
                .collect(Collectors.toList());

        if (filteredRows.isEmpty()) {
            log.info("✅ No new readings to save — all timestamps already exist.");
            return List.of();
        }

        log.info("📌 {} new readings will be saved for meterSerial {}", filteredRows.size(), meterSerial);
        return filteredRows;
    }

    /**
     * 2️⃣ Rewrite persistReadings with partition pre-creation
     * Since you want to:
     * •	Extract all months from the incoming readings
     * •	Pre-create missing partitions once before persisting
     */
    public int persistReadingsByMonth(List<ProfileChannelTwoDTO> filteredRows) {
        Session session = em.unwrap(Session.class);

        log.info("💾 Persisting readings to DB....");
        final AtomicInteger saved = new AtomicInteger();
        filteredRows.forEach(dto -> {
            ProfileChannelTwo entity = ProfileChannelTwoToEntity.toEntity(dto);

            // Check if record exists
            ProfileChannelTwoId id = new ProfileChannelTwoId(
                    entity.getMeterSerial(),
                    entity.getEntryTimestamp()
            );

            ProfileChannelTwo existing = session.get(ProfileChannelTwo.class, id);

            if (existing == null) {
                session.persist(entity); // Insert new
            } else {
                session.merge(entity);   // Update existing
            }

            if (saved.incrementAndGet() % FLUSH_BATCH == 0) {
                session.flush();
                session.clear();
            }
        });

        session.flush();
        session.clear();

        log.info("💾 Saved {} readings to DB.", saved.get());
        return saved.get();
    }
}
