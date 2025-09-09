package com.memmcol.hes.domain.profile;

import com.memmcol.hes.application.port.in.ProfileSyncUseCase;
import com.memmcol.hes.application.port.out.*;
import com.memmcol.hes.infrastructure.dlms.ChannelOneReaderAdapter;
import com.memmcol.hes.infrastructure.dlms.ProfileMetadataProvider;
import com.memmcol.hes.infrastructure.persistence.ChannelOnePersistenceAdapter;
import com.memmcol.hes.model.ModelProfileMetadata;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@AllArgsConstructor
public class ChannelOneService implements ProfileSyncUseCase {
    private final MeterLockPort lockPort;
    private final ProfileMetricsPort metricsPort;
    private final CapturePeriodPort capturePeriodPort;
    private final ProfileTimestampPort timestampPort;
    private final ProfileMetadataProvider metadataProvider;
    private final ChannelOneReaderAdapter channelOneReader;
    private final PartialProfileRecoveryPort recoveryPort;
    private final ProfileStatePort statePort;
    private final ChannelOnePersistenceAdapter channelOnePort;

    @Override
    public void syncUpToNow(String model, String meterSerial, String profileObis, int batchSize) {
        try {
            lockPort.withExclusive(meterSerial, () -> {
                doSync(model, meterSerial, profileObis, batchSize);
                return null;
            });
        } catch (IllegalStateException e2) {
            log.error("Sync fatal meter={} profile={} reason={}", meterSerial, profileObis, e2.getMessage(), e2);
            metricsPort.recordFailure(meterSerial, profileObis, "Server restarted");
        } catch (Exception e) {
            log.error("Sync fatal meter={} profile={} reason={}", meterSerial, profileObis, e.getMessage(), e);
            metricsPort.recordFailure(meterSerial, profileObis, "lock_or_sync_error");
        }
    }

    /**
     * Read profile channel 1 objects and save to DB
     * @param model
     * @param meterSerial
     * @param profileObis
     * @param batchSize
     * @throws ProfileReadException
     */
    private void doSync(String model, String meterSerial, String profileObis, int batchSize) throws ProfileReadException {
        //Step 1: Get last timestamp read from the meter or default to yesterday
        ProfileTimestamp cursor = new ProfileTimestamp(timestampPort.resolveLastTimestamp(meterSerial, profileObis)); // fallback seed
        //Step 2: Get profile capture period
        CapturePeriod cp = new CapturePeriod(capturePeriodPort.resolveCapturePeriodSeconds(meterSerial, profileObis));
        //Step 3: Read in batches until now
        LocalDateTime now = LocalDateTime.now();
        while (cursor.value().isBefore(now)) {
            LocalDateTime from = cursor.value();
            LocalDateTime to = from.plusSeconds((long) batchSize * cp.seconds());
            if (to.isAfter(now)) to = now;

            long t0 = System.currentTimeMillis();
            List<ChannelOneRow> rows = null;
            boolean exceptionOccurred = false;

            //get columns objects and scaler
            ProfileMetadataResult metadataResult = metadataProvider.resolve(meterSerial, profileObis, model);

            //Read from meter
            try {
                rows = channelOneReader.readRange(model, meterSerial, profileObis, metadataResult, from, to);
            } catch (ProfileReadException ex) {
                log.warn("Range read failed; attempting recovery meter={} profile={} cause={}",
                        meterSerial, profileObis, ex.getMessage());
                exceptionOccurred = true;
                rows = attemptRecovery(meterSerial, profileObis);
            }

            // --- Cursor & Salvage Logic ---
            if ((rows == null || rows.isEmpty()) && exceptionOccurred) {
                // 1. Exception + No Rows = BREAK
                log.warn("Breaking: no rows and exception occurred meter={} obis={} from={} to={}",
                        meterSerial, profileObis, from, to);
                break;
            }

            if (rows == null || rows.isEmpty()) {
                // 2. No Exception + No Rows = ADVANCE
                log.info("No rows but no exception — advancing cursor meter={} obis={} from={} to={}",
                        meterSerial, profileObis, from, to);
                cursor = new ProfileTimestamp(to).plus(cp);
                statePort.upsertState(meterSerial, profileObis, new ProfileTimestamp(to), cp);
                continue;
            }

            //Persist to DB
            ProfileSyncResult syncResult = channelOnePort.saveBatchAndAdvanceCursor(meterSerial, model, profileObis, rows, cp, metadataResult);
            long dt2 = System.currentTimeMillis() - t0;

            //Save metrics for report dashboard
            int saved = syncResult.getInsertedCount();
            metricsPort.recordBatch(meterSerial, profileObis, saved, dt2);

            ProfileTimestamp resumeFrom = ProfileTimestamp.ofNullable(syncResult.getAdvanceTo());
            cursor = (resumeFrom != null ? resumeFrom.plus(cp) : cursor.plus(cp));

            // Persist new timestamp cursor
            statePort.upsertState(meterSerial, profileObis, resumeFrom, cp);

            // Safety guard to avoid infinite loop if capture period = 0 (should not happen)
            if (cp.seconds() <= 0) break;
        }
    }

    private List<ChannelOneRow> attemptRecovery(String serial, String obis) {
        try {
            List<ChannelOneRow> salvaged = recoveryPort.recoverPartial(serial, obis);
            metricsPort.recordRecovery(serial, obis, salvaged.size());
            return salvaged;
        } catch (ProfileReadException e) {
            metricsPort.recordFailure(serial, obis, "recovery_failed");
            log.error("Partial recovery failed meter={} profile={}", serial, obis, e);
            return List.of();
        }
    }
}
