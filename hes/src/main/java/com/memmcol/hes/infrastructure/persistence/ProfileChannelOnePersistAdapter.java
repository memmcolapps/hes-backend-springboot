package com.memmcol.hes.infrastructure.persistence;

import com.memmcol.hes.application.port.out.ProfileStatePort;
import com.memmcol.hes.domain.profile.*;
import com.memmcol.hes.dto.ProfileChannelOneDTO;
import com.memmcol.hes.dto.ProfileChannelOneDTOV1;
import com.memmcol.hes.entities.ProfileChannelOne;
import com.memmcol.hes.entities.ProfileChannelOneToEntity;
import com.memmcol.hes.infrastructure.observability.FactTablePersistenceLogging;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
//import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Repository;
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

@Repository
@Slf4j
public class ProfileChannelOnePersistAdapter {
    @PersistenceContext
    private EntityManager entityManager;
    private final ProfileStatePort statePort;
    private static final int FLUSH_BATCH = 100;

    public ProfileChannelOnePersistAdapter(ProfileStatePort statePort) {
        this.statePort = statePort;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProfileSyncResult saveBatchAndAdvanceCursor(String meterSerial,
                                                       String profileOBIS,
                                                       List<ProfileChannelOneDTO> readings,
                                                       CapturePeriod capturePeriodSeconds) {
        final String table = "profile_channel_one";
        final String domain = "profile";
        try {
            ProfileState st = statePort.loadState(meterSerial, profileOBIS);
            LocalDateTime previousLast = (st != null && st.lastTimestamp() != null)
                    ? st.lastTimestamp().value()
                    : null;

            if (readings == null || readings.isEmpty()) {
                FactTablePersistenceLogging.logPersistZeroRows(log, domain, table, meterSerial, "_unknown", profileOBIS,
                        "NO_INCOMING_ROWS", "readings null or empty before persist", 0);
                return new ProfileSyncResult(0, 0, 0, previousLast, previousLast, previousLast, false);
            }

            String meterModel = FactTablePersistenceLogging.firstModel(readings, ProfileChannelOneDTO::getModelNumber);
            int total = readings.size();
            int inserted = persistReadingsByMonth(readings);
            int duplicate = total - inserted;

            LocalDateTime incomingMax = readings.stream()
                    .map(ProfileChannelOneDTO::getEntryTimestamp)
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

            FactTablePersistenceLogging.logBatchOutcome(log, domain, table, meterSerial, meterModel, profileOBIS, inserted, total, duplicate);

            return new ProfileSyncResult(total, inserted, duplicate, previousLast, incomingMax, advanceTo, advanced);
        } catch (Exception e) {
            String meterModel = (readings != null && !readings.isEmpty())
                    ? FactTablePersistenceLogging.firstModel(readings, ProfileChannelOneDTO::getModelNumber)
                    : "_unknown";
            FactTablePersistenceLogging.logPersistFailure(log, domain, table, meterSerial, meterModel, profileOBIS, e);
            throw e;
        }
    }

    /**
     * Batched insert with ON CONFLICT DO NOTHING (idempotent at DB level, no pre-SELECT).
     */
    public int persistReadingsByMonth(List<ProfileChannelOneDTO> filteredRows) {
        if (filteredRows == null || filteredRows.isEmpty()) {
            return 0;
        }

        Session session = entityManager.unwrap(Session.class);

        log.debug("💾 Persisting readings to DB....");
        final AtomicInteger saved = new AtomicInteger();

        session.doWork(connection -> {
            String sql = """
                    INSERT INTO profile_channel_one (
                        meter_serial, model_number, entry_timestamp,
                        meter_health_indicator,
                        instantaneous_voltage_l1, instantaneous_voltage_l2, instantaneous_voltage_l3,
                        instantaneous_current_l1, instantaneous_current_l2, instantaneous_current_l3,
                        instantaneous_active_power,
                        instantaneous_reactive_import, instantaneous_reactive_export,
                        instantaneous_power_factor, instantaneous_apparent_power,
                        instantaneous_net_frequency
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT DO NOTHING
                    """;

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                int pending = 0;

                for (ProfileChannelOneDTO dto : filteredRows) {
                    ProfileChannelOne e = ProfileChannelOneToEntity.toEntity(dto);
                    int i = 1;
                    ps.setString(i++, e.getMeterSerial());
                    ps.setString(i++, e.getModelNumber());
                    ps.setObject(i++, e.getEntryTimestamp());
                    ps.setObject(i++, e.getMeterHealthIndicator(), Types.INTEGER);
                    ps.setObject(i++, e.getInstantaneousVoltageL1(), Types.DOUBLE);
                    ps.setObject(i++, e.getInstantaneousVoltageL2(), Types.DOUBLE);
                    ps.setObject(i++, e.getInstantaneousVoltageL3(), Types.DOUBLE);
                    ps.setObject(i++, e.getInstantaneousCurrentL1(), Types.DOUBLE);
                    ps.setObject(i++, e.getInstantaneousCurrentL2(), Types.DOUBLE);
                    ps.setObject(i++, e.getInstantaneousCurrentL3(), Types.DOUBLE);
                    ps.setObject(i++, e.getInstantaneousActivePower(), Types.DOUBLE);
                    ps.setObject(i++, e.getInstantaneousReactiveImport(), Types.DOUBLE);
                    ps.setObject(i++, e.getInstantaneousReactiveExport(), Types.DOUBLE);
                    ps.setObject(i++, e.getInstantaneousPowerFactor(), Types.DOUBLE);
                    ps.setObject(i++, e.getInstantaneousApparentPower(), Types.DOUBLE);
                    ps.setObject(i++, e.getInstantaneousNetFrequency(), Types.DOUBLE);
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

        log.debug("💾 Saved {} readings to DB.", saved.get());
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

    /**
     * Creates monthly partition tables for any missing months found in the DTO list.
     */
    public void createPartitionsIfMissingV1(List<ProfileChannelOneDTOV1> filteredRows) {
        Session session = entityManager.unwrap(Session.class);

        // 1. Extract unique months (YYYYMM format)
        Set<String> months = filteredRows.stream()
                .map(dto -> dto.getEntryTimestamp().format(DateTimeFormatter.ofPattern("yyyyMM")))
                .collect(Collectors.toSet());

        createPartitionsIfMissingByMonths(session, months);
    }

    /**
     * Same partition creation logic, but for the current DTO type.
     */
    public void createPartitionsIfMissing(List<ProfileChannelOneDTO> filteredRows) {
        Session session = entityManager.unwrap(Session.class);

        Set<String> months = filteredRows.stream()
                .map(ProfileChannelOneDTO::getEntryTimestamp)
                .filter(Objects::nonNull)
                .map(ts -> ts.format(DateTimeFormatter.ofPattern("yyyyMM")))
                .collect(Collectors.toSet());

        createPartitionsIfMissingByMonths(session, months);
    }

    private void createPartitionsIfMissingByMonths(Session session, Set<String> months) {
        // Pre-create missing partitions
        months.forEach(month -> {
            String partitionName = "profile_channel_one_" + month;
            if (PartitionCreationMemo.isConfirmed(partitionName)) {
                return;
            }
            String startDate = month + "01";
            String endDate = YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyyMM"))
                    .plusMonths(1)
                    .atDay(1)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE);

            String sql = String.format(
                    "CREATE TABLE IF NOT EXISTS %s PARTITION OF profile_channel_one " +
                            "FOR VALUES FROM ('%s') TO ('%s');",
                    partitionName, startDate, endDate
            );

            session.doWork(connection -> {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute(sql);
                    PartitionCreationMemo.confirm(partitionName);
                    log.info("🆕 Partition {} created.", partitionName);
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("exists")) {
                        PartitionCreationMemo.confirm(partitionName);
                        log.debug("Partition {} already exists, skipping.", partitionName);
                    } else {
                        log.error("Error creating partition {}: {}", partitionName, e.getMessage());
                    }
                }
            });
        });
    }

}
