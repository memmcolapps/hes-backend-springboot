package com.memmcol.hes.domain.clock;


import com.memmcol.hes.entities.ClockSyncJob;
import com.memmcol.hes.entities.ClockSyncMeter;
import com.memmcol.hes.repository.ClockSyncJobRepository;
import com.memmcol.hes.repository.ClockSyncMeterRepository;
import com.memmcol.hes.repository.MeterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
@Service
@RequiredArgsConstructor
public class ClockSyncRegistrationService {

    private static final int BATCH_SIZE = 1000;

    private static final List<String> BLOCKING_STATUSES = List.of(
            "SUCCESS",
            "PENDING",
            "PROCESSING",
            "REVIEW"
    );

    private final MeterRepository meterRepository;
    private final ClockSyncJobRepository jobRepository;
    private final ClockSyncMeterRepository meterLogRepository;
    private final ClockSyncBatchService batchService;

    @Transactional
    public int registerNextBatch(Long jobId) {

        ClockSyncJob job = jobRepository.findById(jobId)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "Job not found: " + jobId));

        if (job.isRegistrationCompleted()) {
            return 0;
        }

        List<MeterSerialProvider> meters;

        if (job.getLastRegisteredMeterId() == null) {
            meters = meterRepository.findFirstRegisteredMeterBatch(
                    PageRequest.of(0, BATCH_SIZE));
        } else {
            meters = meterRepository.findRegisteredMetersAfter(
                    job.getLastRegisteredMeterId(),
                    PageRequest.of(0, BATCH_SIZE));
        }

        if (meters.isEmpty()) {
            job.setRegistrationCompleted(true);
            jobRepository.save(job);
            return 0;
        }

        // Get distinct, valid meter numbers from this source batch.
        List<String> meterNumbers = meters.stream()
                .map(MeterSerialProvider::getMeterNumber)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(number -> !number.isEmpty())
                .distinct()
                .toList();

        if (!meterNumbers.isEmpty()) {

            // Exclude meters blocked by previous or active jobs.
            Set<String> blockedMeterNumbers =
                    meterLogRepository.findBlockedMeterNumbersForRegistration(
                            meterNumbers);

            // Prevent duplicate log records if this job encounters
            // a meter it has already registered.
            Set<String> alreadyRegistered =
                    meterLogRepository.findExistingMeterNumbersForJob(
                            jobId, meterNumbers);

            List<String> eligibleMeters = meterNumbers.stream()
                    .filter(number ->
                            !blockedMeterNumbers.contains(number))
                    .filter(number ->
                            !alreadyRegistered.contains(number))
                    .toList();

            // Atomically claim eligible meters across concurrent jobs.
            Set<String> claimedMeters = batchService.tryClaimMeters(
                    jobId, eligibleMeters);

            // Register only newly claimed meters.
            List<ClockSyncMeter> records = claimedMeters.stream()
                    .map(meterNumber -> {
                        ClockSyncMeter record = new ClockSyncMeter();
                        record.setJobId(jobId);
                        record.setMeterNumber(meterNumber);
                        record.setStatus("PENDING");
                        record.setAttemptCount(0);
                        return record;
                    })
                    .toList();

            if (!records.isEmpty()) {
                meterLogRepository.saveAll(records);
                job.setTotalMeters(
                        job.getTotalMeters() + records.size());
            }
        }

        // Advance the cursor past every source meter scanned,
        // even when none of the meters could be registered.
        job.setLastRegisteredMeterId(
                meters.get(meters.size() - 1).getId());

        jobRepository.save(job);

        // Return the number scanned, not the number registered.
        return meters.size();
    }
}

