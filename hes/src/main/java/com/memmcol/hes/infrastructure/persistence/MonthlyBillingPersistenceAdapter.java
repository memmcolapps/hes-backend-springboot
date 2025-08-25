package com.memmcol.hes.infrastructure.persistence;

import com.memmcol.hes.application.port.out.ProfileStatePort;
import com.memmcol.hes.domain.profile.CapturePeriod;
import com.memmcol.hes.domain.profile.ProfileState;
import com.memmcol.hes.domain.profile.ProfileSyncResult;
import com.memmcol.hes.domain.profile.ProfileTimestamp;
import com.memmcol.hes.dto.MonthlyBillingDTO;
import com.memmcol.hes.entities.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Repository;

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
public class MonthlyBillingPersistenceAdapter {
    @PersistenceContext
    private EntityManager entityManager;
    private final ProfileStatePort statePort;
    private static final int FLUSH_BATCH = 100;

    public MonthlyBillingPersistenceAdapter(ProfileStatePort statePort) {
        this.statePort = statePort;
    }

    public ProfileSyncResult saveBatchAndAdvanceCursor(String meterSerial,
                                                       String profileOBIS,
                                                       List<MonthlyBillingDTO> readings,
                                                       CapturePeriod capturePeriodSeconds) {
        ProfileState st = statePort.loadState(meterSerial, profileOBIS); // or null if you dropped obis
        LocalDateTime previousLast = (st != null && st.lastTimestamp() != null)
                ? st.lastTimestamp().value()
                : null;

        if (readings == null || readings.isEmpty()) {
            log.info("saveBatchAndAdvanceCursor: no rows for meter={}", meterSerial);
            return new ProfileSyncResult(
                    0, 0, 0, previousLast, previousLast, previousLast, false
            );
        }

        int total = readings.size();
        //Deduplication by timestamp only
        List<MonthlyBillingDTO> filteredRows = deduplicate(meterSerial, readings);
        //Insert filtered rows
        int inserted = persistReadingsByMonth(filteredRows);
        int duplicate = total - inserted;

        LocalDateTime incomingMax = readings.stream()
                .map(MonthlyBillingDTO::getEntryTimestamp)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(previousLast);

        LocalDateTime advanceTo = previousLast == null
                ? incomingMax
                : (previousLast.isAfter(incomingMax) ? previousLast : incomingMax);

        boolean advanced = previousLast == null || advanceTo.isAfter(previousLast);

        if (advanceTo != null) {
            statePort.upsertState(meterSerial, profileOBIS, new ProfileTimestamp(advanceTo), capturePeriodSeconds);
        }

        log.info("Batch persisted meter={} total={} inserted={} dup={} start={} end={} advanceTo={}",
                meterSerial, total, inserted, duplicate, previousLast, incomingMax, advanceTo);

        return new ProfileSyncResult(total, inserted, duplicate, previousLast, incomingMax, advanceTo, advanced);
    }

    /**
     * Deduplicate by checking which timestamps already exist in DB
     */
    private List<MonthlyBillingDTO> deduplicate(String meterSerial, List<MonthlyBillingDTO> readings) {
        if (readings == null || readings.isEmpty()) {
            log.info("⚠ No readings provided for deduplication.");
            return List.of();
        }

        // Extract timestamps from incoming readings
        List<LocalDateTime> incomingTimestamps = readings.stream()
                .map(MonthlyBillingDTO::getEntryTimestamp)
                .collect(Collectors.toList());

        // Query DB for existing timestamps
        List<LocalDateTime> existingTimestamps = findExistingTimestamps(meterSerial, incomingTimestamps);

        // Filter only new readings
        List<MonthlyBillingDTO> filteredRows = readings.stream()
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
     * Find already existing timestamps for a given meter serial
     */
    public List<LocalDateTime> findExistingTimestamps(String serial, List<LocalDateTime> timestamps) {
        if (timestamps == null || timestamps.isEmpty()) {
            return List.of();
        }
        TypedQuery<LocalDateTime> query = entityManager.createQuery(
                "SELECT r.entryTimestamp " +
                        "FROM MonthlyBillingEntity r " +
                        "WHERE r.meterSerial = :serial " +
                        "AND r.entryTimestamp IN :timestamps",
                LocalDateTime.class
        );
        query.setParameter("serial", serial);
        query.setParameter("timestamps", timestamps);
        return query.getResultList();
    }




    /**
     * 2️⃣ Rewrite persistReadings with partition pre-creation
     * Since you want to:
     * •	Extract all months from the incoming readings
     * •	Pre-create missing partitions once before persisting
     */
    public int persistReadingsByMonth(List<MonthlyBillingDTO> filteredRows) {
        Session session = entityManager.unwrap(Session.class);

        log.info("💾 Persisting readings to DB....");
        final AtomicInteger saved = new AtomicInteger();
        filteredRows.forEach(dto -> {
            MonthlyBillingEntity entity = MonthlyBillingToEntity.toEntity(dto);

            // Check if record exists
            MonthlyBillingId id = new MonthlyBillingId(
                    entity.getMeterSerial(),
                    entity.getEntryTimestamp()
            );

            MonthlyBillingEntity existing = session.get(MonthlyBillingEntity.class, id);

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

    /**
     * Creates monthly partition tables for any missing months found in the DTO list.
     */
    public void createPartitionsIfMissing(List<MonthlyBillingDTO> filteredRows) {
        Session session = entityManager.unwrap(Session.class);

        // 1. Extract unique months (YYYYMM format)
        Set<String> months = filteredRows.stream()
                .map(dto -> dto.getEntryTimestamp().format(DateTimeFormatter.ofPattern("yyyyMM")))
                .collect(Collectors.toSet());

        // 2. Pre-create missing partitions
        months.forEach(month -> {
            String partitionName = "monthly_billing_profile_" + month;
            String startDate = month + "01";
            String endDate = YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyyMM"))
                    .plusMonths(1)
                    .atDay(1)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE);

            String sql = String.format(
                    "CREATE TABLE IF NOT EXISTS %s PARTITION OF monthly_billing_profile " +
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

}
