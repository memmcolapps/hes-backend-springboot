package com.memmcol.hes.infrastructure.persistence;

import com.memmcol.hes.application.port.out.ProfileStatePort;
import com.memmcol.hes.domain.profile.CapturePeriod;
import com.memmcol.hes.domain.profile.ProfileState;
import com.memmcol.hes.domain.profile.ProfileSyncResult;
import com.memmcol.hes.domain.profile.ProfileTimestamp;
import com.memmcol.hes.infrastructure.observability.FactTablePersistenceLogging;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
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

@Slf4j
public abstract class AbstractBillingHouseholdPersistenceAdapter<TDto, TEntity, TId> {
    protected static final int FLUSH_BATCH = 200;

    protected abstract EntityManager em();

    protected abstract ProfileStatePort statePort();

    protected abstract Class<TEntity> entityClass();

    protected abstract String baseTableName();

    protected abstract String partitionPrefix();

    protected abstract LocalDateTime entryTimestamp(TDto dto);

    protected abstract TEntity toEntity(TDto dto);

    protected abstract TId toId(TEntity entity);

    /**
     * Allows subclasses to align state cursor advancement to profile cadence.
     * Daily billing typically wants +1 day; monthly billing wants +1 month.
     */
    protected LocalDateTime stateAdvanceTo(LocalDateTime advanceTo) {
        return advanceTo;
    }

    protected List<LocalDateTime> findExistingTimestamps(String serial, List<LocalDateTime> timestamps, String jpqlEntityName) {
        if (timestamps == null || timestamps.isEmpty()) return List.of();
        TypedQuery<LocalDateTime> query = em().createQuery(
                "SELECT r.entryTimestamp FROM " + jpqlEntityName + " r " +
                        "WHERE r.meterSerial = :serial AND r.entryTimestamp IN :timestamps",
                LocalDateTime.class
        );
        query.setParameter("serial", serial);
        query.setParameter("timestamps", timestamps);
        return query.getResultList();
    }

    protected List<TDto> deduplicate(String meterSerial, List<TDto> readings, String jpqlEntityName) {
        List<LocalDateTime> incomingTimestamps = readings.stream()
                .map(this::entryTimestamp)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        List<LocalDateTime> existing = findExistingTimestamps(meterSerial, incomingTimestamps, jpqlEntityName);
        return readings.stream()
                .filter(dto -> entryTimestamp(dto) != null && !existing.contains(entryTimestamp(dto)))
                .collect(Collectors.toList());
    }

    public void createPartitionsIfMissing(List<TDto> rows) {
        Session session = em().unwrap(Session.class);
        Set<String> months = rows.stream()
                .map(this::entryTimestamp)
                .filter(Objects::nonNull)
                .map(ts -> ts.format(DateTimeFormatter.ofPattern("yyyyMM")))
                .collect(Collectors.toSet());

        months.forEach(month -> {
            String partitionName = partitionPrefix() + "_" + month;
            String startDate = month + "01";
            String endDate = YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyyMM"))
                    .plusMonths(1)
                    .atDay(1)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE);

            String sql = String.format(
                    "CREATE TABLE IF NOT EXISTS %s PARTITION OF %s FOR VALUES FROM ('%s') TO ('%s');",
                    partitionName, baseTableName(), startDate, endDate
            );

            session.doWork(connection -> {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute(sql);
                    log.info("🆕 Partition {} created.", partitionName);
                } catch (Exception e) {
                    log.error("Error creating partition {}: {}", partitionName, e.getMessage());
                }
            });
        });
    }

    public int persistReadings(List<TDto> rows) {
        Session session = em().unwrap(Session.class);
        AtomicInteger saved = new AtomicInteger();
        rows.stream()
                .filter(dto -> entryTimestamp(dto) != null)
                .forEach(dto -> {
                    TEntity entity = toEntity(dto);
                    TId id = toId(entity);
                    TEntity existing = session.get(entityClass(), id);
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProfileSyncResult saveBatchAndAdvanceCursor(String meterSerial,
                                                       String profileObis,
                                                       List<TDto> readings,
                                                       CapturePeriod capturePeriodSeconds,
                                                       String jpqlEntityName) {
        final String table = baseTableName();
        final String domain = "billing_household";
        try {
            try {
                Session session0 = em().unwrap(Session.class);
                session0.doWork(conn -> {
                    try {
                        String url = conn.getMetaData() != null ? conn.getMetaData().getURL() : "unknown";
                        String schema = "unknown";
                        try {
                            schema = conn.getSchema();
                        } catch (Exception ignored) {
                        }
                        log.debug("Billing persist begin. table={} meter={} obis={} url={} schema={} incomingRows={}",
                                baseTableName(), meterSerial, profileObis, url, schema, (readings == null ? 0 : readings.size()));
                    } catch (Exception ignored) {
                    }
                });
            } catch (Exception ignored) {
            }

            ProfileState st = statePort().loadState(meterSerial, profileObis);
            LocalDateTime previousLast = (st != null && st.lastTimestamp() != null) ? st.lastTimestamp().value() : null;

            if (readings == null || readings.isEmpty()) {
                FactTablePersistenceLogging.logPersistZeroRows(log, domain, table, meterSerial, "_unknown", profileObis,
                        "NO_INCOMING_ROWS", "No billing household readings in batch.", 0);
                return new ProfileSyncResult(0, 0, 0, previousLast, previousLast, previousLast, false);
            }

            String meterModel = FactTablePersistenceLogging.nz(meterModelFromFirstDto(readings.get(0)));

            int total = readings.size();
            List<TDto> filtered = deduplicate(meterSerial, readings, jpqlEntityName);
            int inserted = persistReadings(filtered);
            int duplicate = total - inserted;

            LocalDateTime incomingMax = readings.stream()
                    .map(this::entryTimestamp)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(previousLast);

            LocalDateTime advanceTo = previousLast == null
                    ? incomingMax
                    : (previousLast.isAfter(incomingMax) ? previousLast : incomingMax);

            boolean advanced = previousLast == null || (advanceTo != null && advanceTo.isAfter(previousLast));
            if (advanceTo != null) {
                LocalDateTime next = stateAdvanceTo(advanceTo);
                statePort().upsertState(meterSerial, profileObis, new ProfileTimestamp(next), capturePeriodSeconds);
            }

            FactTablePersistenceLogging.logBatchOutcome(log, domain, table, meterSerial, meterModel, profileObis, inserted, total, duplicate);

            return new ProfileSyncResult(total, inserted, duplicate, previousLast, incomingMax, advanceTo, advanced);
        } catch (Exception e) {
            String mm = "_unknown";
            if (readings != null && !readings.isEmpty()) {
                try {
                    mm = FactTablePersistenceLogging.nz(meterModelFromFirstDto(readings.get(0)));
                } catch (Exception ignored) {
                }
            }
            FactTablePersistenceLogging.logPersistFailure(log, domain, table, meterSerial, mm, profileObis, e);
            throw e;
        }
    }

    /** Meter model string from the first DTO in a batch (for fact-table observability). */
    protected abstract String meterModelFromFirstDto(TDto dto);
}