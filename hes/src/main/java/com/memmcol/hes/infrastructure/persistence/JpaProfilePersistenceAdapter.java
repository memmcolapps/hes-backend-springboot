package com.memmcol.hes.infrastructure.persistence;

import com.memmcol.hes.application.port.out.ProfilePersistencePort;
import com.memmcol.hes.application.port.out.ProfileStatePort;
import com.memmcol.hes.domain.profile.*;
import com.memmcol.hes.model.ProfileChannel2Reading;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 4.2 JPA Persistence Adapter
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JpaProfilePersistenceAdapter implements ProfilePersistencePort {

    @PersistenceContext
    private final EntityManager em;
    private static final int FLUSH_BATCH = 500;
    //ProfileChannel2ReadingEntity is your existing JPA entity (adapt field names).
    private final ProfileStatePort statePort;

    @Override
    @Transactional
    public int saveBatch(String meterSerial, String model, List<ProfileRow> rows) {
        int saved = 0;
        for (ProfileRow r : rows) {
            try {
                ProfileChannel2Reading e = new ProfileChannel2Reading();
                e.setMeterSerial(meterSerial);
                e.setModelNumber(model);
                e.setEntryTimestamp(r.timestamp().value());
                e.setActiveEnergy(r.activeKwh());
                e.setReactiveEnergy(r.reactiveKvarh());
                e.setRawData(r.rawHex());
                e.setReceivedAt(LocalDateTime.now());

                em.persist(e);
                saved++;

                if (saved % FLUSH_BATCH == 0) {
                    em.flush();
                    em.clear();
                }
            } catch (PersistenceException ex) {
                // Check for constraint violation
                if (ex.getCause() != null &&
                        ex.getCause().getMessage() != null &&
                        ex.getCause().getMessage().contains("already exists")) {
                    log.debug("Skipping duplicate reading for meter={}, ts={}",
                            meterSerial, r.timestamp());
                    em.clear(); // Clear to avoid stuck persistence context
                    continue;
                }
                throw ex; // rethrow for unexpected errors
            }
        }
        return saved;
    }

    @Override
    public ProfileTimestamp findLatestTimestamp(String meterSerial, String model) {
        List<java.time.LocalDateTime> list = em.createQuery("""
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

    @Override
    @Transactional
    public ProfileSyncResult saveBatchAndAdvanceCursor(String meterSerial,
                                                       String meterModel,
                                                       String obis,
                                                       List<ProfileRow> rows,
                                                       CapturePeriod capturePeriodSeconds,
                                                       Map<String, Double> scalers) {

        ProfileState st = statePort.loadState(meterSerial, obis); // or null if you dropped obis
        LocalDateTime previousLast = (st != null && st.lastTimestamp() != null)
                ? st.lastTimestamp().value()
                : null;

        if (rows == null || rows.isEmpty()) {
            log.info("saveBatchAndAdvanceCursor: no rows for meter={}", meterSerial);
            return new ProfileSyncResult(
                    0, 0, 0, previousLast, previousLast, previousLast, false
            );
        }

        int total = rows.size();
        int inserted = doBatchInsertHibernate(meterSerial, meterModel, rows, scalers);
        int duplicate = total - inserted;

        LocalDateTime incomingMax = rows.stream()
                .map(ProfileRow::timestamp)
                .filter(Objects::nonNull)
                .map(ProfileTimestamp::value)
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

        log.info("Batch persisted meter={} total={} inserted={} dup={} start={} end={} advanceTo={}",
                meterSerial, total, inserted, duplicate, previousLast, incomingMax, advanceTo);

        return new ProfileSyncResult(total, inserted, duplicate, previousLast, incomingMax, advanceTo, advanced);
    }

    @Transactional
    public int doBatchInsertHibernate(String meterSerial, String meterModel, List<ProfileRow> rows, Map<String, Double> scalers) {
        int saved = 0;

        Session session = em.unwrap(Session.class);

        for (ProfileRow r : rows) {
            LocalDateTime ts = r.timestamp() != null ? r.timestamp().value() : null;
            if (ts == null) {
                log.debug("Skipping row w/ null timestamp meter={}", meterSerial);
                continue;
            }

            // Check if record exists
            boolean exists = session.createQuery("""
                                select count(r) from ProfileChannel2Reading r 
                                where r.meterSerial = :serial 
                                  and r.entryTimestamp = :ts
                            """, Long.class)
                    .setParameter("serial", meterSerial)
                    .setParameter("ts", ts)
                    .uniqueResult() > 0;

            if (exists) {
                log.debug("Duplicate record skipped meter={} ts={}", meterSerial, ts);
                continue;
            }

            ProfileChannel2Reading entity = new ProfileChannel2Reading();
            entity.setMeterSerial(meterSerial);
            entity.setModelNumber(meterModel);
            entity.setEntryIndex(-1);
            entity.setEntryTimestamp(ts);
            entity.setActiveEnergy(safeParseDouble(r.activeKwh(), "1.0.1.8.0.255", scalers));
            entity.setReactiveEnergy(safeParseDouble(r.reactiveKvarh(), "1.0.2.8.0.255", scalers));
            entity.setRawData(r.rawHex());
            entity.setReceivedAt(LocalDateTime.now());

            session.persist(entity);
            saved++;

            if (saved % FLUSH_BATCH == 0) {
                session.flush();
                session.clear();
            }
        }
        session.flush();
        session.clear();
        return saved;
    }

    private int sumCounts(int[] counts) {
        if (counts == null) return 0;
        int total = 0;
        for (int c : counts) {
            if (c > 0) total += c;
        }
        return total;
    }

    private Double safeParseDouble(Object val, String obis, Map<String, Double> scalers) {
        double result = (double) val;

        if (val instanceof Number) {
            double scaler = scalers.getOrDefault(obis, 1.0);
            result = ((Number) val).doubleValue() * scaler;
            result = BigDecimal.valueOf(((Number) result).doubleValue())
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
        } else
            try {
                result = Double.parseDouble(val.toString());
            } catch (Exception ex) {
                return result;
            }
        return result;
    }
}