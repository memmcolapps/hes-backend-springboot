package com.memmcol.hes.domain.clock;


import com.memmcol.hes.entities.ClockSyncMeter;
import com.memmcol.hes.service.DlmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClockSyncWorker {

    private final ClockSyncBatchService batchService;
    private final ClockSyncResultService resultService;
    private final DlmsService dlmsService;

    public int processNextBatch(Long jobId) {
        List<ClockSyncMeter> batch =
                batchService.claimPendingBatch(jobId);

        for (ClockSyncMeter meter : batch) {
            LocalDateTime targetTime = LocalDateTime.now();

            try {
                resultService.recordAttemptTime(
                        meter.getId(), targetTime);

                Map<String, Object> result = dlmsService.setClock(
                        meter.getMeterNumber(), targetTime);

                // Persist communication evidence first.
                resultService.recordDlmsAudit(meter.getId(), result);

                String dlmsStatus =
                        String.valueOf(result.get("dlmsStatus"));

                boolean success =
                        "success".equalsIgnoreCase(
                                String.valueOf(result.get("status")));

                if (success) {
                    resultService.recordSuccess(meter.getId());
                    continue;
                }

                String message = String.valueOf(result.get("message"));

                boolean hasSentFrames =
                        result.get("requestFrames") instanceof List<?> frames
                                && !frames.isEmpty();

                boolean uncertain =
                        "TIMEOUT".equalsIgnoreCase(dlmsStatus)
                                || ("COMMUNICATION_ERROR".equalsIgnoreCase(dlmsStatus)
                                && hasSentFrames);

                if (uncertain) {
                    resultService.recordReview(meter.getId(), message);
                } else {
                    resultService.recordFailure(meter.getId(), message);
                }

            } catch (Exception e) {
                log.error(
                        "Clock synchronization exception for meter {}",
                        meter.getMeterNumber(),
                        e
                );

                try {
                    resultService.recordWorkerException(
                            meter.getId(),
                            e.getClass().getSimpleName() + ": "
                                    + e.getMessage()
                    );
                } catch (Exception auditException) {
                    log.error(
                            "Could not persist the failure audit for meter {}. "
                                    + "The meter may remain PROCESSING and its "
                                    + "claim must be retained.",
                            meter.getMeterNumber(),
                            auditException
                    );
                }
            }
        }

        return batch.size();
    }
}

//    public int processNextBatch(Long jobId) {
//        List<ClockSyncMeter> batch =
//                batchService.claimPendingBatch(jobId);
//
//        for (ClockSyncMeter meter : batch) {
//            LocalDateTime targetTime = LocalDateTime.now();
//
//            try {
//                // Persist the target time before the network operation.
//                resultService.recordAttemptTime(
//                        meter.getId(), targetTime);
//
//                Map<String, Object> result =
//                        dlmsService.setClock(
//                                meter.getMeterNumber(), targetTime);
//
//                if (result != null
//                        && "success".equalsIgnoreCase(
//                        String.valueOf(result.get("status")))) {
//
//                    resultService.recordSuccess(meter.getId());
//
//                } else {
//                    String message = result == null
//                            ? "DLMS service returned no result."
//                            : String.valueOf(result.get("message"));
//
//                    resultService.recordFailure(meter.getId(), message);
//                }
//
//            } catch (Exception e) {
//                log.error(
//                        "Clock write outcome requires review for meter {}",
//                        meter.getMeterNumber(),
//                        e
//                );
//
//                try {
//                    resultService.recordReview(
//                            meter.getId(),
//                            String.valueOf(e.getMessage()));
//                } catch (Exception loggingError) {
//                    // Leave PROCESSING rather than retrying a potentially
//                    // successful clock write.
//                    log.error(
//                            "Could not persist meter outcome for {}. "
//                                    + "Manual reconciliation is required.",
//                            meter.getMeterNumber(),
//                            loggingError);
//                }
//            }
//        }
//
//        return batch.size();
//    }