package com.memmcol.hes.domain.profile;

import com.memmcol.hes.application.port.out.*;
import com.memmcol.hes.domain.profile.mappers.ProfileChannelTwoMapper;
import com.memmcol.hes.dto.ProfileChannelTwoDTO;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.infrastructure.dlms.ProfileMetadataProvider;
import com.memmcol.hes.infrastructure.persistence.ProfileChannelTwoPersistenceAdapter;
import com.memmcol.hes.repository.MeterRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@Slf4j
@Service
public class ProfileChannelTwoService {
    private final ProfileTimestampPortImpl timestampPort;
    private final CapturePeriodPort capturePeriodPort;
    private final DlmsReaderUtils dlmsReaderUtils;
    private final ProfileMetricsPort metricsPort;
    private final ProfileStatePort statePort;
    private final ProfileMetadataProvider metadataProvider;
    private final ProfileChannelTwoPersistenceAdapter persistenceAdapter;
    private final ProfileChannelTwoMapper profileChannelTwoMapper;
    private final MeterRepository meterRepository;

    public void readProfileAndSave(String model, String meterSerial, String profileObis, boolean isMD) {

        try {
            final String safeObis = (profileObis == null || profileObis.isBlank()) ? "unknown" : profileObis;

            if (profileObis == null || profileObis.isBlank()) {
                log.error("Profile OBIS is null/blank; skipping read meter={} model={}", meterSerial, model);
                metricsPort.recordFailure(meterSerial, safeObis, "missing_profile_obis");
                return;
            }

            // ✅ STEP 1: Seed cursor (same as Channel 1)
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

            // ✅ STEP 2: Capture period normalization
            CapturePeriod cp = new CapturePeriod(
                    capturePeriodPort.resolveCapturePeriodSeconds(meterSerial, profileObis)
            );

            if (cp.seconds() < 900) {
                cp = new CapturePeriod(900);
            }

            LocalDateTime now = LocalDateTime.now();

            // ✅ MAIN LOOP
            while (cursor.value().isBefore(now)) {

                // 🔥 CRITICAL ALIGNMENT WITH CHANNEL 1
                LocalDateTime from = cursor.value();
                LocalDateTime to = from.plusDays(1);

                if (to.isAfter(now)) {
                    to = now;
                }

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

                List<ProfileRowGeneric> rows;

                try {
                    rows = dlmsReaderUtils.readRange(
                            model, meterSerial, profileObis, metadataResult, from, to, isMD
                    );
                } catch (Exception ex) {
                    log.warn("Range read failed; attempting recovery meter={} profile={} cause={}",
                            meterSerial, profileObis, ex.getMessage());
                    exceptionOccurred = true;
                    rows = attemptRecovery(model, meterSerial, profileObis, metadataResult);
                }

                // --- Cursor & Salvage Logic (aligned with Channel 1) ---

                if ((rows == null || rows.isEmpty()) && exceptionOccurred) {
                    log.warn("Breaking (no rows + exception) meter={} profile={} from={} to={}",
                            meterSerial, profileObis, from, to);
                    return;
                }

                if (rows == null || rows.isEmpty()) {
                    log.info("No rows, no exception — advancing cursor, meter={} profile={}",
                            meterSerial, profileObis);

                    cursor = new ProfileTimestamp(to).plus(cp);
                    statePort.upsertState(meterSerial, profileObis, new ProfileTimestamp(to), cp);
                    continue;
                }

                // ✅ Map & Persist
                List<ProfileChannelTwoDTO> dtos =
                        profileChannelTwoMapper.toDTO(rows, meterSerial, model, isMD, metadataResult);

                persistenceAdapter.createPartitionsIfMissing(dtos);

                ProfileSyncResult syncResult =
                        persistenceAdapter.saveBatchAndAdvanceCursor(
                                meterSerial, model, profileObis, dtos, cp, metadataResult
                        );

                long duration = System.currentTimeMillis() - t0;

                metricsPort.recordBatch(
                        meterSerial,
                        profileObis,
                        syncResult.getInsertedCount(),
                        duration
                );

                // ✅ Cursor advancement (strictly aligned)
                ProfileTimestamp resume = ProfileTimestamp.ofNullable(syncResult.getAdvanceTo());

                cursor = (resume != null)
                        ? resume.plus(cp)
                        : cursor.plus(cp);

                statePort.upsertState(meterSerial, profileObis, resume, cp);

                // Safety guard
                if (cp.seconds() <= 0) {
                    log.warn("cp.seconds() <= 0 : {}", cp);
                    return;
                }
            }

        } catch (Exception ex) {

            log.error("Fatal exception while reading profile, meter={}, profile={}: {}",
                    meterSerial, profileObis, ex.getMessage(), ex);

            String safeObis = (profileObis == null || profileObis.isBlank())
                    ? "unknown"
                    : profileObis;

            metricsPort.recordFailure(meterSerial, safeObis, "unhandled_exception");
        }
    }

    public void readProfileAndSaveV1 (String model, String serial, String profileObis, boolean isMD) throws Exception {
        ProfileTimestamp cursor = new ProfileTimestamp(timestampPort.resolveLastTimestamp(serial, profileObis)); // fallback seed
        CapturePeriod cp = new CapturePeriod(capturePeriodPort.resolveCapturePeriodSeconds(serial, profileObis));
        if (cp.seconds() < 900){
            cp = new CapturePeriod(900);
        }
        LocalDateTime now = LocalDateTime.now();
        while (cursor.value().isBefore(now)) {
            LocalDateTime from = cursor.value();
            LocalDateTime to = from.plusDays(1);
            if (to.isAfter(now)) to = now;

            long t0 = System.currentTimeMillis();
                        boolean exceptionOccurred = false;

            //get columns objects and scaler
            ProfileMetadataResult metadataResult = metadataProvider.resolve(serial, profileObis, model);
            List<ProfileRowGeneric> rows = null;

            try {
                rows = dlmsReaderUtils.readRange(model, serial, profileObis, metadataResult, from, to, true);
            } catch (ProfileReadException ex) {
                log.warn("Range read failed; attempting recovery meter={} profile={} cause={}",
                        serial, profileObis, ex.getMessage());
                exceptionOccurred = true;
                rows = attemptRecovery(model, serial, profileObis, metadataResult);
            } catch (Exception ex2) {
                log.warn("Range read failed2; attempting recovery meter={} profile={} cause={}",
                        serial, profileObis, ex2.getMessage());
                exceptionOccurred = true;
                rows = attemptRecovery(model, serial, profileObis, metadataResult);
            }

            // --- Cursor & Salvage Logic ---
            if ((rows == null || rows.isEmpty()) && exceptionOccurred) {
                // 1. Exception + No Rows = BREAK
                log.warn("Breaking: no rows and exception occurred meter={} profileObis={} from={} to={}",
                        serial, profileObis, from, to);
                break;
            }

            if (rows == null || rows.isEmpty()) {
                // 2. No Exception + No Rows = ADVANCE
                log.info("No rows but no exception — advancing cursor meter={} profileObis={} from={} to={}",
                        serial, profileObis, from, to);
                cursor = new ProfileTimestamp(to).plus(cp);
                statePort.upsertState(serial, profileObis, new ProfileTimestamp(to), cp);
                continue;
            }

            // Map, createPartitionsIfMissing & save
            List<ProfileChannelTwoDTO> dtos = profileChannelTwoMapper.toDTO(rows, serial, model, isMD, metadataResult);
            persistenceAdapter.createPartitionsIfMissing(dtos);
            ProfileSyncResult syncResult = persistenceAdapter.saveBatchAndAdvanceCursor(serial, model, profileObis, dtos, cp, metadataResult);
            long dt2 = System.currentTimeMillis() - t0;

            int saved = syncResult.getInsertedCount();
            metricsPort.recordBatch(serial, profileObis, saved, dt2);

            ProfileTimestamp resumeFrom = ProfileTimestamp.ofNullable(syncResult.getAdvanceTo());
            cursor = (resumeFrom != null ? resumeFrom.plus(cp) : cursor.plus(cp));

            // Persist new cursor
            statePort.upsertState(serial, profileObis, resumeFrom, cp);

            // Safety guard to avoid infinite loop if capture period = 0 (should not happen)
            if (cp.seconds() <= 0) break;
        }
    }

    private List<ProfileRowGeneric> attemptRecovery(String model, String serial, String profileObis, ProfileMetadataResult metadataResult) {
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
