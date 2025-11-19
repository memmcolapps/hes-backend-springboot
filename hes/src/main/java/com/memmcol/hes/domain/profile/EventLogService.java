package com.memmcol.hes.domain.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.memmcol.hes.application.port.out.*;
import com.memmcol.hes.domain.profile.mappers.EventLogMapper;
import com.memmcol.hes.dto.EventLogDTO;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.infrastructure.dlms.ProfileMetadataProvider;
import com.memmcol.hes.infrastructure.persistence.EventLogPersistenceAdapter;
import com.memmcol.hes.nettyUtils.RequestResponseService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@AllArgsConstructor
@Slf4j
@Service
public class EventLogService {
    private final ProfileTimestampPortImpl timestampPort;
    private final CapturePeriodPort capturePeriodPort;
    private final DlmsReaderUtils dlmsReaderUtils;
    private final ProfileMetricsPort metricsPort;
    private final ProfileStatePort statePort;
    private final ProfileMetadataProvider metadataProvider;
    private final EventLogPersistenceAdapter eventLogPersistenceAdapter;
    private final EventLogMapper eventLogMapper;

    /*TODO:
    *  1. Remove @Transactional from all other profiles readProfileAndSave method*/
    public void readProfileAndSave(String model, String meterSerial, String profileObis, boolean isMD) {

        try {
            //Step 1: Get last timestamp read from the meter or default to yesterday
            ProfileTimestamp cursor = new ProfileTimestamp(
                    timestampPort.resolveLastTimestamp(meterSerial, profileObis)
            );

            /*TODO:
            *  1. Remove batch size and iteration from this method.
            *  2. Remove test mode
            *  3. Add plus(cp(1)) before the next timestamp start
            * */
            LocalDateTime now = LocalDateTime.now();
            while (cursor.value().isBefore(now)) {
                LocalDateTime from = cursor.value();
                LocalDateTime to = from.plusDays(1);

                if (to.isAfter(now)) to = now;

                long t0 = System.currentTimeMillis();
                boolean exceptionOccurred = false;

                ProfileMetadataResult captureObjects = metadataProvider.resolve(meterSerial, profileObis, model);
                List<ProfileRowGeneric> rawRows;

                try {
                    rawRows = dlmsReaderUtils.readRange(model, meterSerial, profileObis, captureObjects, from, to, isMD);

//                    if (testMode) {
//                        rawRows = dlmsReaderUtils.mockReadRange(model, meterSerial, profileObis, captureObjects, from, to, true);
//                    } else {
//                        rawRows = dlmsReaderUtils.readRange(model, meterSerial, profileObis, captureObjects, from, to, isMD);
//                    }
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
                    cursor = new ProfileTimestamp(to);
                    statePort.upsertState(meterSerial, profileObis, new ProfileTimestamp(to), new CapturePeriod(10));
                    continue;
                }

//                savePartialToJson(rawRows);

                List<EventLogDTO> eventDtos = eventLogMapper.toDTOs(rawRows, meterSerial, model);

                ProfileSyncResult syncResult = eventLogPersistenceAdapter.saveBatch(meterSerial, profileObis, eventDtos);

                long t1 = System.currentTimeMillis() - t0;
                metricsPort.recordBatch(meterSerial, profileObis, syncResult.getInsertedCount(), t1);

                // Persist new cursor (Next iteration)
                cursor = ProfileTimestamp.ofNullable(syncResult.getAdvanceTo().plusSeconds(10));

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


    private void savePartialToJson(List<ProfileRowGeneric> rows) {
        try {
            ObjectMapper mapper = new ObjectMapper()
                    .registerModule(new JavaTimeModule())                          // ✅ Enables Java 8 date/time
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)       // ✅ Use ISO-8601, not numeric timestamps
                    .enable(SerializationFeature.INDENT_OUTPUT);
//            mapper.writeValue(new File("dlms_profile_partial.json"), rows);
            String json = mapper.writeValueAsString(rows);
            log.info("📦 Profile Row Generic:\n{}", json);
            log.info("📝 Profile Row Generic with {} rows", rows.size());
        } catch (IOException e) {
            log.warn("⚠️ Could not save partial profile data", e);
        }
    }



//    public static void main(String[] args) {
//        readProfileAndSave("MMX-313-CT", "202006001314", "0.0.99.98.0.255", 3);
//    }
}
