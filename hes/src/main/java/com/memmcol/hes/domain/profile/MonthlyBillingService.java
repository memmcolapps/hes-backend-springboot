package com.memmcol.hes.domain.profile;

import com.memmcol.hes.application.port.out.CapturePeriodPort;
import com.memmcol.hes.application.port.out.ProfileMetricsPort;
import com.memmcol.hes.application.port.out.ProfileReadException;
import com.memmcol.hes.application.port.out.ProfileStatePort;
import com.memmcol.hes.domain.profile.mappers.MonthlyBillingMapper;
import com.memmcol.hes.dto.MonthlyBillingDTO;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.infrastructure.dlms.ProfileMetadataProvider;
import com.memmcol.hes.infrastructure.persistence.MonthlyBillingPersistenceAdapter;
import com.memmcol.hes.repository.MeterRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@AllArgsConstructor
@Slf4j
@Service
public class MonthlyBillingService {
    private final ProfileTimestampPortImpl timestampPort;
    private final CapturePeriodPort capturePeriodPort;
    private final DlmsReaderUtils dlmsReaderUtils;
    private final ProfileMetricsPort metricsPort;
    private final ProfileStatePort statePort;
    private final ProfileMetadataProvider metadataProvider;
    private final MonthlyBillingMapper billingMapper;
    private final MonthlyBillingPersistenceAdapter monthlyBillingPersistenceAdapter;
    private final MeterRepository meterRepository;

    public void readProfileAndSave(String model, String meterSerial, String profileObis, boolean isMD) {
        try {
            final String safeObis = (profileObis == null || profileObis.isBlank()) ? "unknown" : profileObis;

            // Guard: OBIS must be present
            if (profileObis == null || profileObis.isBlank()) {
                log.error("Profile OBIS is null/blank; skipping read meter={} model={}", meterSerial, model);
                metricsPort.recordFailure(meterSerial, safeObis, "missing_profile_obis");
                return;
            }

            // Step 1: Seed cursor — 3-step priority chain
            // 1) last_timestamp from profile state
            // 2) created_at (date of capture) from meters table
            // 3) fallback: beginning of previous month (monthly billing cadence)
            LocalDateTime seedFrom = null;

            ProfileState st = statePort.loadState(meterSerial, profileObis);
            if (st != null && st.lastTimestamp() != null) {
                seedFrom = st.lastTimestamp().value();
            }

            if (seedFrom == null) {
                seedFrom = meterRepository.findMeterDetailsByMeterNumber(meterSerial)
                        .map(m -> m.getCreatedAt())
                        .orElse(null);
            }

            if (seedFrom == null) {
                seedFrom = LocalDateTime.now()
                        .minusMonths(1)
                        .withDayOfMonth(1)
                        .withHour(0)
                        .withMinute(0)
                        .withSecond(0)
                        .withNano(0); // ✅ midnight, 1st of last month
            }

            ProfileTimestamp cursor = new ProfileTimestamp(seedFrom);

            // Step 2: Monthly billing — capture period is always 1 month in seconds
            CapturePeriod cp = new CapturePeriod(1);

            LocalDateTime now = LocalDateTime.now();

            while (cursor.value().isBefore(now)) {
                LocalDateTime from = cursor.value();
                LocalDateTime to = from.plusMonths(12);

                if (to.isAfter(now)) to = now;

                long t0 = System.currentTimeMillis();
                boolean exceptionOccurred = false;

                final ProfileMetadataResult metadataResult;
                try {
                    metadataResult = metadataProvider.resolve(meterSerial, profileObis, model);
                } catch (Exception metaEx) {
                    log.error("Metadata resolve failed meter={} profile={} model={} cause={}",
                            meterSerial, profileObis, model, metaEx.getMessage(), metaEx);
                    metricsPort.recordFailure(meterSerial, safeObis, "metadata_resolve_failed");
                    return;
                }

                List<ProfileRowGeneric> rawRows;
                try {
                    rawRows = dlmsReaderUtils.readRange(model, meterSerial, profileObis, metadataResult, from, to, isMD);
                } catch (Exception e) {
                    log.warn("Range read failed; attempting recovery meter={} profile={} cause={}",
                            meterSerial, profileObis, e.getMessage());
                    exceptionOccurred = true;
                    rawRows = attemptRecovery(model, meterSerial, profileObis, metadataResult);
                }

                // --- Cursor & Salvage Logic ---
                if ((rawRows == null || rawRows.isEmpty()) && exceptionOccurred) {
                    log.warn("Breaking (no rows + exception) meter={} profile={} from={} to={}",
                            meterSerial, profileObis, from, to);
                    return;
                }

                if (rawRows == null || rawRows.isEmpty()) {
                    log.info("No rows, no exception — advancing cursor, meter={} profile={}", meterSerial, profileObis);
                    cursor = new ProfileTimestamp(to).plus(cp);
                    statePort.upsertState(meterSerial, profileObis, new ProfileTimestamp(to), cp);
                    continue;
                }

                // Map, createPartitionsIfMissing & save
                List<MonthlyBillingDTO> dtos = billingMapper.toDTO(rawRows, meterSerial, model, true, metadataResult);
                monthlyBillingPersistenceAdapter.createPartitionsIfMissing(dtos);
                ProfileSyncResult syncResult = monthlyBillingPersistenceAdapter.saveBatchAndAdvanceCursor(
                        meterSerial, profileObis, dtos, cp);

                long t1 = System.currentTimeMillis() - t0;
                metricsPort.recordBatch(meterSerial, profileObis, syncResult.getInsertedCount(), t1);

                // Persist new cursor — null-safe
                ProfileTimestamp resume = ProfileTimestamp.ofNullable(syncResult.getAdvanceTo());
                cursor = (resume != null) ? resume.plus(cp) : cursor.plus(cp);
                statePort.upsertState(meterSerial, profileObis, resume, cp);

                if (cp.seconds() <= 0) {
                    log.warn("cp.seconds() <= 0 : {}", cp);
                    return;
                }
            }

        } catch (Exception ex) {
            log.error("Fatal exception while reading profile, meter={}, profile={}: {}",
                    meterSerial, profileObis, ex.getMessage(), ex);
            String safeObis = (profileObis == null || profileObis.isBlank()) ? "unknown" : profileObis;
            metricsPort.recordFailure(meterSerial, safeObis, "unhandled_exception");
        }
    }

    public void readProfileAndSaveV1(String model, String meterSerial, String profileObis, boolean isMD) {
        try {
            //Step 1: Get last timestamp read from the meter or default to yesterday
            ProfileTimestamp cursor = new ProfileTimestamp(
                    timestampPort.resolveLastTimestamp(meterSerial, profileObis)
            );
            //Step 2: Get profile capture period
            CapturePeriod cp = new CapturePeriod(1);;

            LocalDateTime now = LocalDateTime.now();

            while (cursor.value().isBefore(now)) {
                LocalDateTime from = cursor.value();
                LocalDateTime to;
                to = from.plusMonths(12);   // Monthly profile → move by full months


                if (to.isAfter(now)) to = now;

                long t0 = System.currentTimeMillis();
                boolean exceptionOccurred = false;

                ProfileMetadataResult metadataResult = metadataProvider.resolve(meterSerial, profileObis, model);
                List<ProfileRowGeneric> rawRows;
                try {
                    rawRows = dlmsReaderUtils.readRange(model, meterSerial, profileObis, metadataResult, from, to, isMD);
                } catch (Exception e) {
                    log.warn("Range read failed; attempting recovery meter={} profile={} cause={}",
                            meterSerial, profileObis, e.getMessage());
                    exceptionOccurred = true;
                    rawRows = attemptRecovery(model, meterSerial, profileObis, metadataResult);
                }

                // --- Cursor & Salvage Logic ---
                if ((rawRows == null || rawRows.isEmpty()) && exceptionOccurred) {
                    // BREAK and exit silently
                    log.warn("Breaking (no rows + exception) meter={} profile={} from={} to={}",
                            meterSerial, profileObis, from, to);
                    return;
                }

                if (rawRows == null || rawRows.isEmpty()) {
                    log.info("No rows, no exception — advancing cursor, meter={} profile={}", meterSerial, profileObis);
                    cursor = new ProfileTimestamp(to.plusMonths(1));
                    statePort.upsertState(meterSerial, profileObis, new ProfileTimestamp(to), cp);
                    continue;
                }

//                 Map, createPartitionsIfMissing & save
                List<MonthlyBillingDTO> dtos = billingMapper.toDTO(rawRows, meterSerial, model, true, metadataResult);
                monthlyBillingPersistenceAdapter.createPartitionsIfMissing(dtos);
                ProfileSyncResult syncResult = monthlyBillingPersistenceAdapter.saveBatchAndAdvanceCursor(meterSerial, profileObis, dtos, cp);

                long t1 = System.currentTimeMillis() - t0;
                metricsPort.recordBatch(meterSerial, profileObis, syncResult.getInsertedCount(), t1);

                // Persist new cursor
                cursor = ProfileTimestamp.ofNullable(syncResult.getAdvanceTo().plusMonths(1));
//                statePort.upsertState(meterSerial, profileObis, resume, cp);

                // Safety guard to avoid infinite loop if capture period = 0 (should not happen)
                if (cp.seconds() <= 0) {
                    log.warn("cp.seconds() <= 0 :  {}", cp);
                    return;
                }
            }

        } catch (Exception ex) {
            // Final safety: log and exit WITHOUT re-throwing.
            log.error("Fatal exception while reading profile, meter={}, profile={}: {}",
                    meterSerial, profileObis, ex.getMessage(), ex);
            metricsPort.recordFailure(meterSerial, profileObis, "unhandled_exception");
        }
    }

    private List<ProfileRowGeneric> attemptRecovery(String model, String serial, String profileObis, ProfileMetadataResult metadataResult) {
        /*TODO:
         *  1. I have encountered timeout error.
         *  2. I want to debug the decoding of partial rows.
         *  3. After debuging and resolving, then delete the neccessary block in timestampdecoder class
         * */
        try {
            List<ProfileRowGeneric> salvaged = dlmsReaderUtils.recoverPartial(serial, profileObis, model, metadataResult);
            metricsPort.recordRecovery(serial, profileObis, salvaged.size());
            return salvaged;
        } catch (ProfileReadException e) {
            metricsPort.recordFailure(serial, profileObis, "recovery_failed");
            log.error("Partial recovery failed meter={} profile={}", serial, profileObis, e);
            return List.of();
        }
    }

}
