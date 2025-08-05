package com.memmcol.hes.domain.profile;

import com.memmcol.hes.application.port.in.ProfileSyncUseCase;
import com.memmcol.hes.application.port.out.*;
import com.memmcol.hes.infrastructure.dlms.ProfileMetadataProvider;
import com.memmcol.hes.model.ModelProfileMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 3. Application Service (Orchestrator)
 */
@Slf4j
@RequiredArgsConstructor
public class ProfileSyncService implements ProfileSyncUseCase {
    private final ProfileStatePort statePort;
    private final ProfileDataReaderPort readerPort;
    private final PartialProfileRecoveryPort recoveryPort;
    private final ProfilePersistencePort persistencePort;
    private final MeterLockPort lockPort;
    private final ProfileMetricsPort metricsPort;
    private final CapturePeriodPort capturePeriodPort;
    private final ProfileTimestampPort timestampPort;
    private final ProfileMetadataProvider metadataProvider;
    private final APIClientPort apiClientPort;

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

    private void doSync(String model, String serial, String obis, int batchSize) throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, IOException, NoSuchAlgorithmException, BadPaddingException, SignatureException, InvalidKeyException {
//        ProfileState state = statePort.loadState(serial, obis); //Check DB only. I will add
//        ProfileTimestamp cursor = (state != null && state.lastTimestamp() != null)
//                ? state.lastTimestamp()
//                : new ProfileTimestamp(profileTimestampResolver2.resolveLastTimestamp(serial, obis)); // fallback seed
//        CapturePeriod cp = (state != null && state.capturePeriod() != null)
//                ? state.capturePeriod()
//                : new CapturePeriod(capturePeriodPort.resolveCapturePeriodSeconds(serial, obis));

        ProfileTimestamp cursor = new ProfileTimestamp(timestampPort.resolveLastTimestamp(serial, obis)); // fallback seed
        CapturePeriod cp = new CapturePeriod(capturePeriodPort.resolveCapturePeriodSeconds(serial, obis));

        LocalDateTime now = LocalDateTime.now();
        while (cursor.value().isBefore(now)) {
            LocalDateTime from = cursor.value();
            LocalDateTime to = from.plusSeconds((long) batchSize * cp.seconds());
            if (to.isAfter(now)) to = now;

            long t0 = System.currentTimeMillis();
            List<ProfileRow> rows = null;
            boolean exceptionOccurred = false;

            //get columns objects and scaler
            ProfileMetadataResult result = metadataProvider.resolve(serial, obis, model);
            // 🎯 Use metadata
            List<ModelProfileMetadata> metadataList = result.getMetadataList();
            // 🎯 Use scalers
            Map<String, Double> scalers = result.getScalers();

            try {
                rows = readerPort.readRange(model, serial, obis, metadataList, from, to);
            } catch (ProfileReadException ex) {
                log.warn("Range read failed; attempting recovery meter={} profile={} cause={}",
                        serial, obis, ex.getMessage());
                exceptionOccurred = true;
                rows = attemptRecovery(serial, obis);
            } catch (Exception ex2) {
                log.warn("Range read failed2; attempting recovery meter={} profile={} cause={}",
                        serial, obis, ex2.getMessage());
                exceptionOccurred = true;
                rows = attemptRecovery(serial, obis);
            }

            // --- Cursor & Salvage Logic ---
            if ((rows == null || rows.isEmpty()) && exceptionOccurred) {
                // 1. Exception + No Rows = BREAK
                log.warn("Breaking: no rows and exception occurred meter={} obis={} from={} to={}",
                        serial, obis, from, to);
                break;
            }

            if (rows == null || rows.isEmpty()) {
                // 2. No Exception + No Rows = ADVANCE
                log.info("No rows but no exception — advancing cursor meter={} obis={} from={} to={}",
                        serial, obis, from, to);
                cursor = new ProfileTimestamp(to).plus(cp);
                statePort.upsertState(serial, obis, new ProfileTimestamp(to), cp);
                continue;
            }

//            int saved = persistencePort.saveBatch(serial, obis, rows);
//            long dt = System.currentTimeMillis() - t0;
//            metricsPort.recordBatch(serial, obis, saved, dt);
//
//            ProfileRow last = rows.get(rows.size() - 1);
//            cursor = last.timestamp().plus(cp);
//
//            statePort.upsertState(serial, obis, last.timestamp(), cp);


            ProfileSyncResult syncResult = persistencePort.saveBatchAndAdvanceCursor(serial, model, obis, rows, cp, scalers);
            long dt2 = System.currentTimeMillis() - t0;

            int saved = syncResult.getInsertedCount();
            metricsPort.recordBatch(serial, obis, saved, dt2);

            ProfileTimestamp resumeFrom = ProfileTimestamp.ofNullable(syncResult.getAdvanceTo());
            cursor = (resumeFrom != null ? resumeFrom.plus(cp) : cursor.plus(cp));

            // Persist new cursor
            statePort.upsertState(serial, obis, resumeFrom, cp);

//            log.info("Batch persisted meter={} total={} inserted={} dup={} start={} end={} advanceTo={}",
//                    serial, rows.size(), saved, rows.size() - saved,
//                    from, syncResult.getIncomingMax(), resumeFrom);

            // Safety guard to avoid infinite loop if capture period = 0 (should not happen)
            if (cp.seconds() <= 0) break;
        }
        readerPort.sendDisconnectRequest(serial);
    }

    private List<ProfileRow> attemptRecovery(String serial, String obis) {
        try {
            List<ProfileRow> salvaged = recoveryPort.recoverPartial(serial, obis);
            metricsPort.recordRecovery(serial, obis, salvaged.size());
            return salvaged;
        } catch (ProfileReadException e) {
            metricsPort.recordFailure(serial, obis, "recovery_failed");
            log.error("Partial recovery failed meter={} profile={}", serial, obis, e);
            return List.of();
        }
    }
}