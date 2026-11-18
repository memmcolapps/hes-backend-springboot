package com.memmcol.hes.domain;


import com.memmcol.hes.entities.ClockSyncJob;
import com.memmcol.hes.repository.ClockSyncJobRepository;
import com.memmcol.hes.repository.ClockSyncMeterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClockSyncJobProgressService {

    private final ClockSyncJobRepository jobRepository;
    private final ClockSyncMeterRepository meterRepository;

    @Transactional
    public void markRunning(Long jobId) {
        ClockSyncJob job = getJob(jobId);
        job.setStatus("RUNNING");

        if (job.getStartedAt() == null) {
            job.setStartedAt(LocalDateTime.now());
        }
    }

    @Transactional
    public void finishIfComplete(Long jobId) {
        ClockSyncJob job = getJob(jobId);

        if (!job.isRegistrationCompleted()) {
            return;
        }

        long pending =
                meterRepository.countByJobIdAndStatus(jobId, "PENDING");
        long processing =
                meterRepository.countByJobIdAndStatus(jobId, "PROCESSING");

        if (pending > 0 || processing > 0) {
            return;
        }

        long failed =
                meterRepository.countByJobIdAndStatus(jobId, "FAILED");
        long review =
                meterRepository.countByJobIdAndStatus(jobId, "REVIEW");

        job.setStatus(failed == 0 && review == 0
                ? "COMPLETED"
                : "COMPLETED_WITH_ERRORS");

        job.setCompletedAt(LocalDateTime.now());
    }

    @Transactional
    public void markJobFailed(Long jobId, String error) {
        ClockSyncJob job = getJob(jobId);
        job.setStatus("FAILED");
        job.setErrorMessage(error);
        job.setCompletedAt(LocalDateTime.now());
    }

    private ClockSyncJob getJob(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() ->
                        new NoSuchElementException("Job not found: " + jobId));
    }
}

