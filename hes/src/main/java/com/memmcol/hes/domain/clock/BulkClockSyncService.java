package com.memmcol.hes.domain.clock;

import com.memmcol.hes.entities.ClockSyncJob;
import com.memmcol.hes.entities.ClockSyncMeter;
import com.memmcol.hes.repository.ClockSyncJobRepository;
import com.memmcol.hes.repository.ClockSyncMeterRepository;
import com.memmcol.hes.repository.MeterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;


@Service
@RequiredArgsConstructor
public class BulkClockSyncService {

    private final ClockSyncJobRepository jobRepository;
    private final ClockSyncMeterRepository meterRepository;
    private final ClockSyncCoordinator coordinator;
    private final MeterRepository registeredMeterRepository;
    private final ClockSyncBatchService batchService;

    public synchronized Map<String, Object> startSynchronization(
            List<String> meterNumbers) {

        if (jobRepository.existsByStatus("RUNNING")) {
            throw new IllegalStateException(
                    "A clock synchronization job is already running.");
        }

        if (meterNumbers == null || meterNumbers.isEmpty()) {
            return startBulkSynchronization();
        }

        return startSelectedMeterSynchronization(meterNumbers);
    }

    private Map<String, Object> startBulkSynchronization() {
        ClockSyncJob job = createJob(false);

        coordinator.runJob(job.getId());

        return buildStartResponse(
                job.getId(),
                "Bulk clock synchronization has been queued.");
    }

    private Map<String, Object> startSelectedMeterSynchronization(
            List<String> requestedMeters) {

        // 1. Remove blanks and duplicate serial numbers.
        List<String> requested = requestedMeters.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();

        if (requested.isEmpty()) {
            throw new IllegalArgumentException(
                    "Provide at least one valid meter number.");
        }

        // 2. Find registered meters with SmartMeterInfo.
        Set<String> existing = new HashSet<>(
                registeredMeterRepository
                        .findExistingRegisteredMeterNumbers(requested));

        List<String> validMeters = requested.stream()
                .filter(existing::contains)
                .toList();

        List<String> invalidMeters = requested.stream()
                .filter(meter -> !existing.contains(meter))
                .toList();

        // 3. Create a job even when all supplied meters are invalid.
        ClockSyncJob job = createJob(true);

        // 4. Claim valid meters to prevent concurrent processing.
        Set<String> claimedMeters = validMeters.isEmpty()
                ? Collections.emptySet()
                : batchService.tryClaimMeters(job.getId(), validMeters);

        LocalDateTime now = LocalDateTime.now();
        List<ClockSyncMeter> records = new ArrayList<>();

        // 5. Create a log record for every requested meter.
        for (String meterNumber : requested) {
            ClockSyncMeter record = new ClockSyncMeter();

            record.setJobId(job.getId());
            record.setMeterNumber(meterNumber);
            record.setAttemptCount(0);
            record.setCreatedAt(now);
            record.setUpdatedAt(now);

            if (!existing.contains(meterNumber)) {
                // Meter does not exist in the registered meter database.
                record.setStatus("FAILED");
                record.setLastError(
                        "Meter is not registered or has no associated SmartMeterInfo.");
                record.setProcessedAt(now);

            } else if (!claimedMeters.contains(meterNumber)) {
                // Another job currently owns this meter's claim.
                record.setStatus("FAILED");
                record.setLastError(
                        "Meter is already claimed by another synchronization job.");
                record.setProcessedAt(now);

            } else {
                // Eligible meter: ready for the DLMS clock write.
                record.setStatus("PENDING");
            }

            records.add(record);
        }

        // 6. Persist all logs, including missing meters.
        meterRepository.saveAll(records);

        // 7. Total includes valid, missing, and already-claimed meters.
        job.setTotalMeters(records.size());
        jobRepository.save(job);

        // 8. Run asynchronously so valid meters can still be processed.
        coordinator.runJob(job.getId());

        // Keep your existing response-building code here.
        Map<String, Object> response = new HashMap<>();
        response.put("jobId", job.getId());
        response.put("status", "RUNNING");
        response.put("totalMeters", records.size());
        response.put("validMeters", validMeters.size());
        response.put("invalidMeters", invalidMeters);
        response.put("acceptedForProcessing", claimedMeters.size());

        return response;
    }

//    private Map<String, Object> startSelectedMeterSynchronization(
//            List<String> requestedMeters) {
//
//        // Remove blanks and duplicate serial numbers.
//        List<String> requested = requestedMeters.stream()
//                .filter(Objects::nonNull)
//                .map(String::trim)
//                .filter(s -> !s.isEmpty())
//                .distinct()
//                .toList();
//
//        if (requested.isEmpty()) {
//            throw new IllegalArgumentException(
//                    "Provide at least one valid meter number.");
//        }
//
//        // Find meters registered in the database with SmartMeterInfo.
//        Set<String> existing = new HashSet<>(
//                registeredMeterRepository
//                        .findExistingRegisteredMeterNumbers(requested));
//
//        List<String> invalidMeters = requested.stream()
//                .filter(meter -> !existing.contains(meter))
//                .toList();
//
//        List<String> validMeters = requested.stream()
//                .filter(existing::contains)
//                .toList();
//
//        if (validMeters.isEmpty()) {
//            throw new IllegalArgumentException(
//                    "None of the supplied meter numbers exists in the "
//                            + "registered meter database. Invalid meters: "
//                            + invalidMeters);
//        }
//
//        // Mark registration complete because this job uses an explicit list.
//        ClockSyncJob job = createJob(true);
//
//        List<ClockSyncMeter> records = validMeters.stream()
//                .map(meterNumber -> {
//                    ClockSyncMeter record = new ClockSyncMeter();
//                    record.setJobId(job.getId());
//                    record.setMeterNumber(meterNumber);
//                    record.setStatus("PENDING");
//                    record.setAttemptCount(0);
//                    return record;
//                })
//                .toList();
//
//        meterRepository.saveAll(records);
//
//        job.setTotalMeters(records.size());
//        jobRepository.saveAndFlush(job);
//
//        coordinator.runJob(job.getId());
//
//        Map<String, Object> response = buildStartResponse(
//                job.getId(),
//                "Clock synchronization has been queued for the "
//                        + "requested meters.");
//
//        response.put("requestedCount", requested.size());
//        response.put("acceptedCount", validMeters.size());
//        response.put("invalidCount", invalidMeters.size());
//        response.put("invalidMeters", invalidMeters);
//
//        return response;
//    }

    private ClockSyncJob createJob(boolean registrationCompleted) {
        ClockSyncJob job = new ClockSyncJob();
        job.setStatus("RUNNING");
        job.setStartedAt(LocalDateTime.now());
        job.setTotalMeters(0);
        job.setRegistrationCompleted(registrationCompleted);

        return jobRepository.saveAndFlush(job);
    }

    private Map<String, Object> buildStartResponse(
            Long jobId, String message) {

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jobId", jobId);
        response.put("status", "RUNNING");
        response.put("message", message);

        return response;
    }


    @Transactional(readOnly = true)
    public Map<String, Object> getJobStatus(Long jobId) {

        System.out.println("kkkk:: "+jobId);

        if (jobId == null) {
            List<ClockSyncJob> jobs = jobRepository.findAll();

            List<Map<String, Object>> jobStatuses = jobs.stream()
                    .map(job -> buildJobStatus(job))
                    .toList();

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("totalJobs", jobs.size());
            response.put("jobs", jobStatuses);

            return response;
        }

        ClockSyncJob job = jobRepository.findById(jobId)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "Clock sync job not found: " + jobId));

        return buildJobStatus(job);
    }

    private Map<String, Object> buildJobStatus(ClockSyncJob job) {

        Long id = job.getId();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jobId", id);
        response.put("status", job.getStatus());
        response.put("totalMeters", job.getTotalMeters());
        response.put("registrationCompleted",
                job.isRegistrationCompleted());

        response.put("successful",
                meterRepository.countByJobIdAndStatus(id, "SUCCESS"));
        response.put("failed",
                meterRepository.countByJobIdAndStatus(id, "FAILED"));
        response.put("review",
                meterRepository.countByJobIdAndStatus(id, "REVIEW"));
        response.put("pending",
                meterRepository.countByJobIdAndStatus(id, "PENDING"));
        response.put("processing",
                meterRepository.countByJobIdAndStatus(id, "PROCESSING"));

        response.put("createdAt", job.getCreatedAt());
        response.put("startedAt", job.getStartedAt());
        response.put("completedAt", job.getCompletedAt());
        response.put("errorMessage", job.getErrorMessage());

        return response;
    }


//    @Transactional(readOnly = true)
//    public Map<String, Object> getJobStatus(Long jobId) {
//        ClockSyncJob job = jobRepository.findById(jobId)
//                .orElseThrow(() ->
//                        new NoSuchElementException(
//                                "Clock sync job not found: " + jobId));
//
//        Map<String, Object> response = new LinkedHashMap<>();
//        response.put("jobId", job.getId());
//        response.put("status", job.getStatus());
//        response.put("totalMeters", job.getTotalMeters());
//        response.put("registrationCompleted",
//                job.isRegistrationCompleted());
//        response.put("successful",
//                meterRepository.countByJobIdAndStatus(jobId, "SUCCESS"));
//        response.put("failed",
//                meterRepository.countByJobIdAndStatus(jobId, "FAILED"));
//        response.put("review",
//                meterRepository.countByJobIdAndStatus(jobId, "REVIEW"));
//        response.put("pending",
//                meterRepository.countByJobIdAndStatus(jobId, "PENDING"));
//        response.put("processing",
//                meterRepository.countByJobIdAndStatus(jobId, "PROCESSING"));
//        response.put("createdAt", job.getCreatedAt());
//        response.put("startedAt", job.getStartedAt());
//        response.put("completedAt", job.getCompletedAt());
//        response.put("errorMessage", job.getErrorMessage());
//
//        return response;
//    }

    @Transactional(readOnly = true)
    public Page<ClockSyncMeter> getMeterLogs(
            Long jobId,
            String status,
            Pageable pageable) {

        if (!jobRepository.existsById(jobId)) {
            throw new NoSuchElementException(
                    "Clock sync job not found: " + jobId);
        }

        if (status == null || status.isBlank()) {

            return meterRepository.findByJobId(jobId, pageable);
        }

        return meterRepository.findByJobIdAndStatus(
                jobId, status.toUpperCase(Locale.ROOT), pageable);
    }
}


//    public synchronized Map<String, Object> startSynchronization(List<String> meterNumbers) {
//        if (jobRepository.existsByStatus("RUNNING")) {
//            throw new IllegalStateException(
//                    "A bulk clock synchronization is already running.");
//        }
//
//        ClockSyncJob job = new ClockSyncJob();
////        job.setId(UUID.randomUUID());
//        job.setStatus("PENDING");
//        job.setTotalMeters(0);
//        job.setRegistrationCompleted(false);
//
//        // Persist the job before starting background execution.
//        jobRepository.saveAndFlush(job);
//
//        try {
//            Long jobId = job.getId();
//            coordinator.runJob(jobId);
//        } catch (Exception e) {
//            job.setStatus("FAILED");
//            job.setErrorMessage(String.valueOf(e.getMessage()));
//            job.setCompletedAt(LocalDateTime.now());
//            jobRepository.save(job);
//            throw e;
//        }
//
//        Map<String, Object> response = new LinkedHashMap<>();
//        response.put("jobId", job.getId());
//        response.put("status", "PENDING");
//        response.put("message",
//                "Clock correction has been queued for background processing.");
//
//        return response;
//    }


//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class BulkClockSyncService {
//
//    private final DlmsService dlmsService;
//    private final MeterRepository meterRepository;
//
//    @Qualifier("clockSyncExecutor")
//    private final Executor clockSyncExecutor;
//
//    private final Map<String, ClockSyncJob> jobs =
//            new ConcurrentHashMap<>();
//
//    public synchronized Map<String, Object> startSynchronization() {
//        boolean alreadyRunning = jobs.values().stream()
//                .anyMatch(job -> "RUNNING".equals(job.status));
//
//        if (alreadyRunning) {
//            throw new IllegalStateException(
//                    "A bulk clock synchronization is already running."
//            );
//        }
//
//        List<String> meterNumbers =
//                meterRepository.findAllRegisteredMeterNumbers();
//
//        String jobId = UUID.randomUUID().toString();
//
//        ClockSyncJob job = new ClockSyncJob(meterNumbers.size());
//        jobs.put(jobId, job);
//
//        try {
//            CompletableFuture.runAsync(
//                    () -> processMeters(jobId, meterNumbers),
//                    clockSyncExecutor
//            );
//        } catch (Exception e) {
//            jobs.remove(jobId);
//            throw e;
//        }
//
//        Map<String, Object> response = new LinkedHashMap<>();
//        response.put("jobId", jobId);
//        response.put("status", "RUNNING");
//        response.put("message",
//                "Bulk clock synchronization started in the background.");
//        response.put("totalMeters", meterNumbers.size());
//
//        return response;
//    }
//
//    private void processMeters(
//            String jobId,
//            List<String> meterNumbers) {
//
//        ClockSyncJob job = jobs.get(jobId);
//
//        try {
//            for (String meterNumber : meterNumbers) {
//                try {
//                    // Capture the time immediately before each meter write.
//                    LocalDateTime targetDateTime = LocalDateTime.now();
//
//                    Map<String, Object> result =
//                            dlmsService.setClock(meterNumber, targetDateTime);
//
//                    job.results.add(result);
//
//                    if ("success".equalsIgnoreCase(
//                            String.valueOf(result.get("status")))) {
//                        job.successful.incrementAndGet();
//                    } else {
//                        job.failed.incrementAndGet();
//                    }
//
//                } catch (Exception e) {
//                    job.failed.incrementAndGet();
//
//                    log.error(
//                            "Clock synchronization failed for meter {}",
//                            meterNumber,
//                            e
//                    );
//
//                    Map<String, Object> failure = new LinkedHashMap<>();
//                    failure.put("serial", meterNumber);
//                    failure.put("status", "failed");
//                    failure.put("message",
//                            "Clock write failed: "
//                                    + String.valueOf(e.getMessage()));
//
//                    job.results.add(failure);
//
//                } finally {
//                    job.processed.incrementAndGet();
//                }
//            }
//
//            job.status = "COMPLETED";
//
//        } catch (Exception e) {
//            job.status = "FAILED";
//            log.error("Bulk clock synchronization job {} failed", jobId, e);
//
//        } finally {
//            job.completedAt = LocalDateTime.now();
//        }
//    }
//
//    public Map<String, Object> getJobStatus(String jobId) {
//        ClockSyncJob job = jobs.get(jobId);
//
//        if (job == null) {
//            throw new NoSuchElementException(
//                    "Clock synchronization job not found: " + jobId
//            );
//        }
//
//        Map<String, Object> response = new LinkedHashMap<>();
//        response.put("jobId", jobId);
//        response.put("status", job.status);
//        response.put("totalMeters", job.totalMeters);
//        response.put("processed", job.processed.get());
//        response.put("successful", job.successful.get());
//        response.put("failed", job.failed.get());
//        response.put("results", new ArrayList<>(job.results));
//        response.put("completedAt", job.completedAt);
//
//        return response;
//    }
//
//    private static class ClockSyncJob {
//        private final int totalMeters;
//        private final AtomicInteger processed = new AtomicInteger();
//        private final AtomicInteger successful = new AtomicInteger();
//        private final AtomicInteger failed = new AtomicInteger();
//        private final List<Map<String, Object>> results =
//                new CopyOnWriteArrayList<>();
//
//        private volatile String status = "RUNNING";
//        private volatile LocalDateTime completedAt;
//
//        private ClockSyncJob(int totalMeters) {
//            this.totalMeters = totalMeters;
//        }
//    }
//}





















//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class BulkClockSyncService {
//
//    private final DlmsService dlmsService;
//    private final MeterRepository meterRepository;
//
//    public Map<String, Object> synchronizeAllMeters() {
//
//        // Capture the current time immediately before each meter write.
//        LocalDateTime targetDateTime = LocalDateTime.now();
//
//        List<String> meterNumbers =
//                meterRepository.findAllRegisteredMeterNumbers();
//
//        List<Map<String, Object>> results = new ArrayList<>();
//
//        int successful = 0;
//        int failed = 0;
//
//        for (String meterNumber : meterNumbers) {
//
//
//            try {
//                Map<String, Object> result =
//                        dlmsService.setClock(meterNumber, targetDateTime);
//
//                results.add(result);
//
//                if ("success".equalsIgnoreCase(
//                        String.valueOf(result.get("status")))) {
//                    successful++;
//                } else {
//                    failed++;
//                }
//
//            } catch (Exception e) {
//                failed++;
//
//                log.error(
//                        "Clock synchronization failed for meter {}",
//                        meterNumber,
//                        e
//                );
//
//                Map<String, Object> failure = new LinkedHashMap<>();
//                failure.put("serial", meterNumber);
//                failure.put("status", "failed");
//                failure.put("message",
//                        "Clock write failed: " + String.valueOf(e.getMessage()));
//
//                results.add(failure);
//            }
//        }
//
//        Map<String, Object> response = new LinkedHashMap<>();
//        response.put("totalMeters", meterNumbers.size());
//        response.put("successful", successful);
//        response.put("failed", failed);
//        response.put("results", results);
//        response.put("targetDateTime", targetDateTime);
//
//        return response;
//    }
//}








//    public Map<String, Object> synchronizeAllMeters() {
//
//        // Capture the target time once for the entire operation.
//        LocalDateTime targetDateTime = LocalDateTime.now();
//
//        List<String> serials =
//                meterRepository.findAllRegisteredMeterSerials();
//
//        List<Map<String, Object>> results = new ArrayList<>();
//
//        int successful = 0;
//        int failed = 0;
//
//        for (String serial : serials) {
//            try {
//                Map<String, Object> result =
//                        dlmsService.setClock(serial, targetDateTime);
//
//                results.add(result);
//
//                if ("success".equalsIgnoreCase(
//                        String.valueOf(result.get("status")))) {
//                    successful++;
//                } else {
//                    failed++;
//                }
//
//            } catch (Exception e) {
//                failed++;
//
//                log.error("Clock synchronization failed for meter {}",
//                        serial, e);
//
//                results.add(Map.of(
//                        "serial", serial,
//                        "status", "failed",
//                        "message", "Clock write failed: "
//                                + e.getMessage()
//                ));
//            }
//        }
//
//        Map<String, Object> response = new LinkedHashMap<>();
//        response.put("targetDateTime", targetDateTime);
//        response.put("totalMeters", serials.size());
//        response.put("successful", successful);
//        response.put("failed", failed);
//        response.put("results", results);
//
//        return response;
//    }
//}
//
//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class MeterClockSyncService {
//
//    private final DlmsService dlmsService;
//    private final MeterSerialProvider meterSerialProvider;
//
//    public Map<String, Object> synchronizeClocks() {
//
//        List<String> serials = meterSerialProvider.getMeterSerials();
//
//        LocalDateTime targetDateTime = LocalDateTime.now();
//
//        List<Map<String, Object>> results = new ArrayList<>();
//
//        int successful = 0;
//        int failed = 0;
//
//        for (String serial : serials) {
//            try {
//                Map<String, Object> result =
//                        dlmsService.setClock(serial, targetDateTime);
//
//                results.add(result);
//
//                if ("success".equalsIgnoreCase(
//                        String.valueOf(result.get("status")))) {
//                    successful++;
//                } else {
//                    failed++;
//                }
//
//            } catch (Exception e) {
//                failed++;
//
//                log.error(
//                        "Failed to synchronize clock for meter {}",
//                        serial,
//                        e
//                );
//
//                Map<String, Object> result = new HashMap<>();
//                result.put("serial", serial);
//                result.put("status", "failed");
//                result.put("message", e.getMessage());
//                result.put("timestamp", LocalDateTime.now());
//
//                results.add(result);
//            }
//        }
//
//        Map<String, Object> response = new HashMap<>();
//        response.put("status", failed == 0 ? "success" : "completed_with_errors");
//        response.put("targetDateTime", targetDateTime);
//        response.put("totalMeters", serials.size());
//        response.put("successful", successful);
//        response.put("failed", failed);
//        response.put("results", results);
//
//        return response;
//    }
//}
