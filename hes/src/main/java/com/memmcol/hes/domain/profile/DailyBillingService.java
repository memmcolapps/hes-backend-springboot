package com.memmcol.hes.domain.profile;

import com.memmcol.hes.application.port.out.CapturePeriodPort;
import com.memmcol.hes.application.port.out.ProfileMetricsPort;
import com.memmcol.hes.application.port.out.ProfileReadException;
import com.memmcol.hes.application.port.out.ProfileStatePort;
import com.memmcol.hes.domain.profile.mappers.DailyBillingMapper;
import com.memmcol.hes.dto.DailyBillingProfileDTO;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.infrastructure.dlms.ProfileMetadataProvider;
import com.memmcol.hes.infrastructure.persistence.DailyBillingPersistenceAdapter;
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
public class DailyBillingService {
    private final ProfileTimestampPortImpl timestampPort;
    private final CapturePeriodPort capturePeriodPort;
    private final DlmsReaderUtils dlmsReaderUtils;
    private final ProfileMetricsPort metricsPort;
    private final ProfileStatePort statePort;
    private final ProfileMetadataProvider metadataProvider;
    private final DailyBillingMapper dailyBillingMapper;
    private final DailyBillingPersistenceAdapter dailyBillingPersistenceAdapter;
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
            // 3) fallback: now - 1 day
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
                seedFrom = LocalDateTime.now().minusDays(1);
            }

            ProfileTimestamp cursor = new ProfileTimestamp(seedFrom);

            // Step 2: Daily billing — capture period is always 1 day
            CapturePeriod cp = new CapturePeriod(1);

            LocalDateTime now = LocalDateTime.now();

            while (cursor.value().isBefore(now)) {
                LocalDateTime from = cursor.value();
                LocalDateTime to = from.plusDays(1);

                if (to.isAfter(now)) to = now;

                long t0 = System.currentTimeMillis();
                boolean exceptionOccurred = false;

                final ProfileMetadataResult captureObjects;
                try {
                    captureObjects = metadataProvider.resolve(meterSerial, profileObis, model);
                } catch (Exception metaEx) {
                    log.error("Metadata resolve failed meter={} profile={} model={} cause={}",
                            meterSerial, profileObis, model, metaEx.getMessage(), metaEx);
                    metricsPort.recordFailure(meterSerial, safeObis, "metadata_resolve_failed");
                    return;
                }

                List<ProfileRowGeneric> rawRows;
                try {
                    rawRows = dlmsReaderUtils.readRange(model, meterSerial, profileObis, captureObjects, from, to, isMD);
                } catch (Exception e) {
                    log.warn("Range read failed; attempting recovery meter={} profile={} cause={}",
                            meterSerial, profileObis, e.getMessage());
                    exceptionOccurred = true;
                    rawRows = attemptRecovery(model, meterSerial, profileObis, captureObjects);
                }

                // --- Cursor & Salvage Logic ---
                if ((rawRows == null || rawRows.isEmpty()) && exceptionOccurred) {
                    log.warn("Breaking (no rows + exception) meter={} profile={} from={} to={}",
                            meterSerial, profileObis, from, to);
                    return;
                }

                if (rawRows == null || rawRows.isEmpty()) {
                     log.warn("Empty profile response meter={} profile={} from={} to={}",
                            meterSerial, profileObis, from, to);
                    cursor = new ProfileTimestamp(to);   // ALWAYS move forward deterministically
                    continue;
                }

                List<DailyBillingProfileDTO> dtos = dailyBillingMapper.toDTO(rawRows, meterSerial, model, true, captureObjects);
                dailyBillingPersistenceAdapter.createPartitionsIfMissing(dtos);
                ProfileSyncResult syncResult = dailyBillingPersistenceAdapter.saveBatchAndAdvanceCursor(
                        meterSerial, profileObis, dtos, cp);

                long t1 = System.currentTimeMillis() - t0;
                metricsPort.recordBatch(meterSerial, profileObis, syncResult.getInsertedCount(), t1);

                // Persist new cursor — null-safe, consistent with Methods 1 & 2
                ProfileTimestamp resume = ProfileTimestamp.ofNullable(syncResult.getAdvanceTo());

                cursor = (resume != null)
              ? resume
              : new ProfileTimestamp(to);

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


    public List<ProfileRowGeneric> attemptRecovery(String model, String serial, String profileObis, ProfileMetadataResult captureObjects) {
        /*TODO:
         *  1. I have not encountered timeout error.
         *  2. I want to debug the decoding of partial rows.
         *  3. After debuging and resolving, then delete the neccessary block in timestampdecoder class
         * */
        try {
            List<ProfileRowGeneric> salvaged = dlmsReaderUtils.recoverPartial(serial, profileObis, model, captureObjects);
            metricsPort.recordRecovery(serial, profileObis, salvaged.size());
            return salvaged;
        } catch (ProfileReadException e) {
            metricsPort.recordFailure(serial, profileObis, "recovery_failed");
            log.error("Partial recovery failed meter={} profile={}", serial, profileObis, e);
            return List.of();
        }
    }
}
