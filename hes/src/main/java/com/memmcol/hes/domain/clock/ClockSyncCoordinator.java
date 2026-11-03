package com.memmcol.hes.domain.clock;


import com.memmcol.hes.domain.ClockSyncJobProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClockSyncCoordinator {

    private final ClockSyncRegistrationService registrationService;
    private final ClockSyncWorker worker;
    private final ClockSyncJobProgressService progressService;

    @Async("clockSyncExecutor")
    public void runJob(Long jobId) {
        try {
            progressService.markRunning(jobId);

            while (true) {
                int scanned = registrationService.registerNextBatch(jobId);

                // Drain pending work before registering the next batch.
                while (worker.processNextBatch(jobId) > 0) {
                    // Continue until there are no more pending records.
                }

                if (scanned == 0) {
                    break;
                }
            }

            progressService.finishIfComplete(jobId);

        } catch (Exception e) {
            log.error("Clock sync job {} failed", jobId, e);

            try {
                progressService.markJobFailed(
                        jobId, String.valueOf(e.getMessage()));
            } catch (Exception loggingError) {
                log.error(
                        "Could not update job status for {}",
                        jobId,
                        loggingError);
            }
        }
    }
//    @Async("clockSyncExecutor")
//    public void runJob(Long jobId) {
//        try {
//            progressService.markRunning(jobId);
//
//            // Register all eligible meters in batches of 1,000.
//            while (true) {
//                int registered =
//                        registrationService.registerNextBatch(jobId);
//
//                if (registered == 0) {
//                    break;
//                }
//            }
//
//            // Attempt each registered meter at most once.
//            while (true) {
//                int processed = worker.processNextBatch(jobId);
//
//                if (processed == 0) {
//                    break;
//                }
//            }
//
//            progressService.finishIfComplete(jobId);
//
//        } catch (Exception e) {
//            log.error("Clock sync job {} failed", jobId, e);
//
//            try {
//                progressService.markJobFailed(
//                        jobId, String.valueOf(e.getMessage()));
//            } catch (Exception loggingError) {
//                log.error(
//                        "Could not update job status for {}",
//                        jobId, loggingError);
//            }
//        }
//    }
}

