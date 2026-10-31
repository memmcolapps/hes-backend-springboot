package com.memmcol.hes.infrastructure.persistence;

import com.memmcol.hes.application.port.out.ProfileStatePort;
import com.memmcol.hes.domain.profile.CapturePeriod;
import com.memmcol.hes.domain.profile.ProfileState;
import com.memmcol.hes.domain.profile.ProfileSyncResult;
import com.memmcol.hes.domain.profile.ProfileTimestamp;
import com.memmcol.hes.dto.ProfileChannelThreeHouseholdDTO;
import com.memmcol.hes.entities.ProfileChannelThreeHousehold;
import com.memmcol.hes.entities.ProfileChannelThreeHouseholdToEntity;
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
public class ProfileChannelThreeHouseholdPersistenceAdapter {
    @PersistenceContext
    private final EntityManager em;
    private static final int FLUSH_BATCH = 500;
    private final ProfileStatePort statePort;

    public void createPartitionsIfMissing(List<ProfileChannelThreeHouseholdDTO> filteredRows) {
        Session session = em.unwrap(Session.class);

        Set<String> months = filteredRows.stream()
                .map(ProfileChannelThreeHouseholdDTO::getEntryTimestamp)
                .filter(Objects::nonNull)
                .map(ts -> ts.format(DateTimeFormatter.ofPattern("yyyyMM")))
                .collect(Collectors.toSet());

        months.forEach(month -> {
            String partitionName = "profile_channel_three_hh_" + month;
            if (PartitionCreationMemo.isConfirmed(partitionName)) {
                return;
            }
            String startDate = month + "01";
            String endDate = YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyyMM"))
                    .plusMonths(1)
                    .atDay(1)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE);

            String sql = String.format(
                    "CREATE TABLE IF NOT EXISTS %s PARTITION OF profile_channel_three_hh " +
                            "FOR VALUES FROM ('%s') TO ('%s');",
                    partitionName, startDate, endDate
            );

            session.doWork(connection -> {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute(sql);
                    PartitionCreationMemo.confirm(partitionName);
                    log.info("Partition {} created.", partitionName);
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProfileSyncResult saveBatchAndAdvanceCursor(String meterSerial,
                                                       String obis,
                                                       List<ProfileChannelThreeHouseholdDTO> readings,
                                                       CapturePeriod capturePeriodSeconds) {
        final String table = "profile_channel_three_hh";
        final String domain = "profile";
        try {
            ProfileState st = statePort.loadState(meterSerial, obis);
            LocalDateTime previousLast = (st != null && st.lastTimestamp() != null)
                    ? st.lastTimestamp().value()
                    : null;

            if (readings == null || readings.isEmpty()) {
                FactTablePersistenceLogging.logPersistZeroRows(log, domain, table, meterSerial, "_unknown", obis,
                        "NO_INCOMING_ROWS", "readings null or empty before persist", 0);
                return new ProfileSyncResult(0, 0, 0, previousLast, previousLast, previousLast, false);
            }

            String meterModel = FactTablePersistenceLogging.firstModel(readings, ProfileChannelThreeHouseholdDTO::getModelNumber);
            int total = readings.size();
            int inserted = persistReadingsByMonth(readings);
            int duplicate = total - inserted;

            LocalDateTime incomingMax = readings.stream()
                    .map(ProfileChannelThreeHouseholdDTO::getEntryTimestamp)
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

            FactTablePersistenceLogging.logBatchOutcome(log, domain, table, meterSerial, meterModel, obis, inserted, total, duplicate);

            return new ProfileSyncResult(total, inserted, duplicate, previousLast, incomingMax, advanceTo, advanced);
        } catch (Exception e) {
            String meterModel = (readings != null && !readings.isEmpty())
                    ? FactTablePersistenceLogging.firstModel(readings, ProfileChannelThreeHouseholdDTO::getModelNumber)
                    : "_unknown";
            FactTablePersistenceLogging.logPersistFailure(log, domain, table, meterSerial, meterModel, obis, e);
            throw e;
        }
    }

    public int persistReadingsByMonth(List<ProfileChannelThreeHouseholdDTO> filteredRows) {
        if (filteredRows == null || filteredRows.isEmpty()) {
            return 0;
        }

        Session session = em.unwrap(Session.class);
        final AtomicInteger saved = new AtomicInteger();

        session.doWork(connection -> {
            String sql = """
                    INSERT INTO profile_channel_three_hh (
                        meter_serial, model_number, entry_timestamp,
                        active_power_l1, active_power_l2, active_power_l3,
                        power_factor_l1, power_factor_l2, power_factor_l3,
                        grid_frequency
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT DO NOTHING
                    """;

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                int pending = 0;

                for (ProfileChannelThreeHouseholdDTO dto : filteredRows) {
                    if (dto.getEntryTimestamp() == null) {
                        continue;
                    }
                    ProfileChannelThreeHousehold e = ProfileChannelThreeHouseholdToEntity.toEntity(dto);
                    int i = 1;
                    ps.setString(i++, e.getMeterSerial());
                    ps.setString(i++, e.getModelNumber());
                    ps.setObject(i++, e.getEntryTimestamp());
                    ps.setObject(i++, e.getActivePowerL1(), Types.DOUBLE);
                    ps.setObject(i++, e.getActivePowerL2(), Types.DOUBLE);
                    ps.setObject(i++, e.getActivePowerL3(), Types.DOUBLE);
                    ps.setObject(i++, e.getPowerFactorL1(), Types.DOUBLE);
                    ps.setObject(i++, e.getPowerFactorL2(), Types.DOUBLE);
                    ps.setObject(i++, e.getPowerFactorL3(), Types.DOUBLE);
                    ps.setObject(i++, e.getGridFrequency(), Types.DOUBLE);
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
