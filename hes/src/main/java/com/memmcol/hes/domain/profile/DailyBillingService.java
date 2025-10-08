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


    public void readProfileAndSave(String model, String meterSerial, String profileObis, boolean isMD) {
        try {
            /*TODO:
            *  My intention for the initial timestamp for the initial profile reading.
            *  1. Get the first time login frame was sent to the HES.
            *  2. Get the date the meter was captured newly.
            *  3. Yesterday if load profile and event and daily billing
            *  4. Last month if monthly billing
            *  5. I don't want to rely on the first timestamp saved on the meter.*/
            //Step 1: Get last timestamp read from the meter or default to yesterday
            ProfileTimestamp cursor = new ProfileTimestamp(
                    timestampPort.resolveLastTimestamp(meterSerial, profileObis)
            );
            //Dummy capture period
            CapturePeriod cp = new CapturePeriod(1);

            LocalDateTime now = LocalDateTime.now();

            while (cursor.value().isBefore(now)) {
                LocalDateTime from = cursor.value();
                LocalDateTime to;
                to = from.plusDays(15);

                if (to.isAfter(now)) to = now;

                long t0 = System.currentTimeMillis();
                boolean exceptionOccurred = false;

                ProfileMetadataResult captureObjects = metadataProvider.resolve(meterSerial, profileObis, model);
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
                    // BREAK and exit silently
                    log.warn("Breaking (no rows + exception) meter={} profile={} from={} to={}",
                            meterSerial, profileObis, from, to);
                    return;
                }

                if (rawRows == null || rawRows.isEmpty()) {
                    log.info("No rows, no exception — advancing cursor, meter={} profile={}", meterSerial, profileObis);
                    cursor = new ProfileTimestamp(to.plusDays(1));

                    statePort.upsertState(meterSerial, profileObis, new ProfileTimestamp(to), new CapturePeriod(1));
                    continue;
                }

                List<DailyBillingProfileDTO> dtos = dailyBillingMapper.toDTO(rawRows, meterSerial, model, true, captureObjects);
                dailyBillingPersistenceAdapter.createPartitionsIfMissing(dtos);
                ProfileSyncResult syncResult = dailyBillingPersistenceAdapter.saveBatchAndAdvanceCursor(meterSerial, profileObis, dtos, cp);

                long t1 = System.currentTimeMillis() - t0;
                metricsPort.recordBatch(meterSerial, profileObis, syncResult.getInsertedCount(), t1);

                // Persist new cursor (Next iteration)
                cursor = ProfileTimestamp.ofNullable(syncResult.getAdvanceTo().plusDays(1));
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
