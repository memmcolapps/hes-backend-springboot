package com.memmcol.hes.infrastructure.persistence;

import com.memmcol.hes.application.port.out.ProfileStatePort;
import com.memmcol.hes.domain.profile.*;
import com.memmcol.hes.dto.ProfileChannelTwoDTO;
import com.memmcol.hes.entities.*;
import com.memmcol.hes.infrastructure.observability.FactTablePersistenceLogging;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
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
            if (PartitionCreationMemo.isConfirmed(partitionName)) {
                return;
            }
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
                    PartitionCreationMemo.confirm(partitionName);
                    log.info("🆕 Partition {} created.", partitionName);
                } catch (Exception e) {
                    if (e.getMessage().contains("exists")) {
                        PartitionCreationMemo.confirm(partitionName);
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
            int inserted = persistReadingsByMonth(readings);
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

    public int persistReadingsByMonth(List<ProfileChannelTwoDTO> filteredRows) {
        if (filteredRows == null || filteredRows.isEmpty()) {
            return 0;
        }

        Session session = em.unwrap(Session.class);
        final AtomicInteger saved = new AtomicInteger();

        session.doWork(connection -> {
            String sql = """
                    INSERT INTO profile_channel_two (
                        meter_serial, entry_timestamp, model_number,
                        meter_health_indicator,
                        active_energy_import,
                        active_energy_import_rate1, active_energy_import_rate2,
                        active_energy_import_rate3, active_energy_import_rate4,
                        active_energy_combined_total, active_energy_export,
                        reactive_energy_import, reactive_energy_export,
                        apparent_energy_import, apparent_energy_export,
                        received_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT DO NOTHING
                    """;

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                int pending = 0;

                for (ProfileChannelTwoDTO dto : filteredRows) {
                    ProfileChannelTwo e = ProfileChannelTwoToEntity.toEntity(dto);
                    if (e.getEntryTimestamp() == null) {
                        continue;
                    }
                    int i = 1;
                    ps.setString(i++, e.getMeterSerial());
                    ps.setObject(i++, e.getEntryTimestamp());
                    ps.setString(i++, e.getModelNumber());
                    ps.setObject(i++, e.getMeterHealthIndicator(), Types.INTEGER);
                    ps.setObject(i++, e.getActiveEnergyImport(), Types.DOUBLE);
                    ps.setObject(i++, e.getActiveEnergyImportRate1(), Types.DOUBLE);
                    ps.setObject(i++, e.getActiveEnergyImportRate2(), Types.DOUBLE);
                    ps.setObject(i++, e.getActiveEnergyImportRate3(), Types.DOUBLE);
                    ps.setObject(i++, e.getActiveEnergyImportRate4(), Types.DOUBLE);
                    ps.setObject(i++, e.getActiveEnergyCombinedTotal(), Types.DOUBLE);
                    ps.setObject(i++, e.getActiveEnergyExport(), Types.DOUBLE);
                    ps.setObject(i++, e.getReactiveEnergyImport(), Types.DOUBLE);
                    ps.setObject(i++, e.getReactiveEnergyExport(), Types.DOUBLE);
                    ps.setObject(i++, e.getApparentEnergyImport(), Types.DOUBLE);
                    ps.setObject(i++, e.getApparentEnergyExport(), Types.DOUBLE);
                    ps.setObject(i++, e.getReceivedAt(), Types.TIMESTAMP);
                    ps.addBatch();

                    if (++pending % FLUSH_BATCH == 0) {
                        saved.addAndGet(sumBatch(ps.executeBatch()));
                        ps.clearBatch();
                    }
                }

                if (pending % FLUSH_BATCH != 0) {
                    saved.addAndGet(sumBatch(ps.executeBatch()));
                }
            }
        });

        return saved.get();
    }

    private static int sumBatch(int[] counts) {
        int sum = 0;
        for (int c : counts) {
            if (c > 0) {
                sum += c;
            } else if (c == Statement.SUCCESS_NO_INFO) {
                sum += 1;
            }
        }
        return sum;
    }
}
