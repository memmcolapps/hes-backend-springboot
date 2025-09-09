package com.memmcol.hes.domain.profile;

import com.memmcol.hes.application.port.in.ProfileSyncUseCase;
import com.memmcol.hes.application.port.out.*;
import com.memmcol.hes.infrastructure.dlms.ProfileMetadataProvider;
import com.memmcol.hes.model.ModelProfileMetadata;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 3. Application Service (Orchestrator)
 */
@Slf4j
@RequiredArgsConstructor
public class ChannelTwoService implements ProfileSyncUseCase {
    private final ProfileStatePort statePort;
    private final Map<String, ProfileDataReaderPort> readerPort;
    private final Map<String, PartialProfileRecoveryPort> recoveryPort;
    private final ProfilePersistencePort persistencePort;
    private final MeterLockPort lockPort;
    private final ProfileMetricsPort metricsPort;
    private final CapturePeriodPort capturePeriodPort;
    private final ProfileTimestampPort timestampPort;
    private final ProfileMetadataProvider metadataProvider;

    @Override
    public void syncUpToNow(String model, String meterSerial, String profileObis, int batchSize) {
        try {
            lockPort.withExclusive(meterSerial, () -> {
                doSync(model, meterSerial, profileObis, batchSize);
                log.info("Profile reading completed successfully. meter={} profile={}", meterSerial, profileObis);
                return null;
            });
        } catch (IllegalStateException e2) {
            log.error("Sync fatal meter={} profile={} reason={}", meterSerial, profileObis, e2.getMessage(), e2);
            assert metricsPort != null;
            metricsPort.recordFailure(meterSerial, profileObis, "Server restarted");
        } catch (Exception e) {
            log.error("Sync fatal meter={} profile={} reason={}", meterSerial, profileObis, e.getMessage(), e);
            assert metricsPort != null;
            metricsPort.recordFailure(meterSerial, profileObis, "lock_or_sync_error");
        }
    }

    private String resolveAdapterKey(String model, String serial, String profileObis) {
        // e.g. based on meter model/serial/Profile OBIS code
        return model + "-" + serial + "-" + profileObis;
    }

    private void doSync(String model, String serial, String profileObis, int batchSize) throws ProfileReadException {
//        ProfileState state = statePort.loadState(serial, profileObis); //Check DB only. I will add
//        ProfileTimestamp cursor = (state != null && state.lastTimestamp() != null)
//                ? state.lastTimestamp()
//                : new ProfileTimestamp(profileTimestampResolver2.resolveLastTimestamp(serial, profileObis)); // fallback seed
//        CapturePeriod cp = (state != null && state.capturePeriod() != null)
//                ? state.capturePeriod()
//                : new CapturePeriod(capturePeriodPort.resolveCapturePeriodSeconds(serial, profileObis));

        ProfileTimestamp cursor = new ProfileTimestamp(timestampPort.resolveLastTimestamp(serial, profileObis)); // fallback seed
        CapturePeriod cp = new CapturePeriod(capturePeriodPort.resolveCapturePeriodSeconds(serial, profileObis));

        LocalDateTime now = LocalDateTime.now();
        while (cursor.value().isBefore(now)) {
            LocalDateTime from = cursor.value();
            LocalDateTime to = from.plusSeconds((long) batchSize * cp.seconds());;
            if (to.isAfter(now)) to = now;

            long t0 = System.currentTimeMillis();
            List<ProfileRow> rows = null;
            boolean exceptionOccurred = false;

            //get columns objects and scaler
            ProfileMetadataResult metadataResult = metadataProvider.resolve(serial, profileObis, model);
            // 🎯 Use metadata


            // ✅ Get a single mapping by OBIS
            ProfileMetadataResult.ProfileMappingInfo mapping = metadataResult.forMapping("1-0:1.8.0");
            if (mapping != null) {
                log.info("Index: {}", mapping.getCaptureIndex());
               log.info("Column: {}", mapping.getColumnName());
            }

            // ✅ Get a single persistence info by OBIS
            ProfileMetadataResult.ProfilePersistenceInfo persistence = metadataResult.forPersistence("1-0:1.8.0");
            if (persistence != null) {
                log.info("Scaler: {}", persistence.getScaler());
                log.info("MultiplyBy: {}", persistence.getMultiplyBy());
            }
            // 🎯 Use scalers
            Map<String, Double> scalers = new HashMap<>();

            try {
                ProfileDataReaderPort readerAdapter = readerPort.get(metadataProvider.resolveAdapterKey(profileObis));
                if (readerAdapter == null) {
                    throw new IllegalArgumentException("No readerAdapter found for: " + readerAdapter);
                }
                rows = readerAdapter.readRange(model, serial, profileObis, metadataResult, from, to);
            } catch (ProfileReadException ex) {
                log.warn("Range read failed; attempting recovery meter={} profile={} cause={}",
                        serial, profileObis, ex.getMessage());
                exceptionOccurred = true;
                rows = attemptRecovery(model, serial, profileObis);
            } catch (Exception ex2) {
                log.warn("Range read failed2; attempting recovery meter={} profile={} cause={}",
                        serial, profileObis, ex2.getMessage());
                exceptionOccurred = true;
                rows = attemptRecovery(model, serial, profileObis);
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

//            int saved = persistencePort.saveBatch(serial, profileObis, rows);
//            long dt = System.currentTimeMillis() - t0;
//            metricsPort.recordBatch(serial, profileObis, saved, dt);
//
//            ProfileRow last = rows.get(rows.size() - 1);
//            cursor = last.timestamp().plus(cp);
//
//            statePort.upsertState(serial, profileObis, last.timestamp(), cp);


            ProfileSyncResult syncResult = persistencePort.saveBatchAndAdvanceCursor(serial, model, profileObis, rows, cp, metadataResult);
            long dt2 = System.currentTimeMillis() - t0;

            int saved = syncResult.getInsertedCount();
            metricsPort.recordBatch(serial, profileObis, saved, dt2);

            ProfileTimestamp resumeFrom = ProfileTimestamp.ofNullable(syncResult.getAdvanceTo());
            cursor = (resumeFrom != null ? resumeFrom.plus(cp) : cursor.plus(cp));

            // Persist new cursor
            statePort.upsertState(serial, profileObis, resumeFrom, cp);

//            log.info("Batch persisted meter={} total={} inserted={} dup={} start={} end={} advanceTo={}",
//                    serial, rows.size(), saved, rows.size() - saved,
//                    from, syncResult.getIncomingMax(), resumeFrom);

            // Safety guard to avoid infinite loop if capture period = 0 (should not happen)
            if (cp.seconds() <= 0) break;
        }
    }

    private List<ProfileRow> attemptRecovery(String model, String serial, String profileObis) {
        try {
            String recoveryPortKey = resolveAdapterKey(model, serial, profileObis);
            PartialProfileRecoveryPort recoveryAdapter = recoveryPort.get(recoveryPortKey);
            if (recoveryAdapter == null) {
                throw new IllegalArgumentException("No adapter found for: " + recoveryAdapter);
            }
            List<ProfileRow> salvaged = recoveryAdapter.recoverPartial(serial, profileObis);
            metricsPort.recordRecovery(serial, profileObis, salvaged.size());
            return salvaged;
        } catch (ProfileReadException e) {
            metricsPort.recordFailure(serial, profileObis, "recovery_failed");
            log.error("Partial recovery failed meter={} profile={}", serial, profileObis, e);
            return List.of();
        }
    }

    private void readProfileProcessor(){

    }
}