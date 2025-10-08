package com.memmcol.hes.domain.profile;

import com.memmcol.hes.application.port.out.*;
import com.memmcol.hes.domain.profile.mappers.ProfileChannelTwoMapper;
import com.memmcol.hes.dto.ProfileChannelTwoDTO;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.infrastructure.dlms.ProfileMetadataProvider;
import com.memmcol.hes.infrastructure.persistence.ProfileChannelTwoPersistenceAdapter;
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


    public void readProfileAndSave (String model, String serial, String profileObis, boolean isMD) throws Exception {
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
