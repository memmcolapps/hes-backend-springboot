package com.memmcol.hes.domain.clock;


import com.memmcol.hes.entities.ClockSyncMeter;
import com.memmcol.hes.repository.ClockSyncMeterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ClockSyncResultService {

    private final ClockSyncMeterRepository repository;
    private final ClockSyncBatchService batchService;

    @Transactional
    public void recordAttemptTime(Long id, LocalDateTime targetTime) {
        ClockSyncMeter meter = getMeter(id);
        meter.setTargetDateTime(targetTime);
        meter.setUpdatedAt(LocalDateTime.now());
    }

    @Transactional
    public void recordSuccess(Long id) {
        ClockSyncMeter meter = getMeter(id);
        meter.setStatus("SUCCESS");
        meter.setLastError(null);
        meter.setProcessedAt(LocalDateTime.now());
        meter.setUpdatedAt(LocalDateTime.now());

        batchService.releaseClaim(meter.getMeterNumber(), meter.getJobId());
    }

    @Transactional
    public void recordFailure(Long id, String message) {
        ClockSyncMeter meter = getMeter(id);
        meter.setStatus("FAILED");
        meter.setLastError(message);
        meter.setProcessedAt(LocalDateTime.now());
        meter.setUpdatedAt(LocalDateTime.now());

        batchService.releaseClaim(meter.getMeterNumber(), meter.getJobId());
    }

    @Transactional
    public void recordReview(Long id, String message) {
        ClockSyncMeter meter = getMeter(id);
        meter.setStatus("REVIEW");
        meter.setLastError(message);
        meter.setProcessedAt(LocalDateTime.now());
        meter.setUpdatedAt(LocalDateTime.now());

        batchService.releaseClaim(meter.getMeterNumber(), meter.getJobId());
    }

    @Transactional
    public void resolveReview(Long id, boolean allowRetry, String resolution) {
        ClockSyncMeter meter = getMeter(id);

        if (!"REVIEW".equals(meter.getStatus())) {
            throw new IllegalStateException(
                    "Only meters with REVIEW status can be resolved.");
        }

        if (resolution == null || resolution.isBlank()) {
            throw new IllegalArgumentException(
                    "A resolution note is required.");
        }

        if (allowRetry) {
            // Investigation confirms another attempt is safe.
            meter.setStatus("FAILED");
            meter.setLastError(
                    "Manual review: " + resolution
                            + ". A new synchronization attempt is permitted.");
            meter.setProcessedAt(LocalDateTime.now());
            meter.setUpdatedAt(LocalDateTime.now());

            batchService.releaseClaim(
                    meter.getMeterNumber(), meter.getJobId());
        } else {
            // Investigation confirms no retry is required.
            meter.setStatus("SUCCESS");
            meter.setLastError(null);
            meter.setProcessedAt(LocalDateTime.now());
            meter.setUpdatedAt(LocalDateTime.now());

            batchService.releaseClaim(
                    meter.getMeterNumber(), meter.getJobId());
        }
    }

    @Transactional
    public void recordDlmsAudit(
            Long id,
            Map<String, Object> result) {

        ClockSyncMeter meter = getMeter(id);

        meter.setTargetDateTime(
                (LocalDateTime) result.get("targetDateTime"));

        meter.setWriteStartedAt(
                (LocalDateTime) result.get("writeStartedAt"));

        meter.setWriteCompletedAt(
                (LocalDateTime) result.get("writeCompletedAt"));

//        meter.setDlmsStatus(
//                (String) result.get("dlmsStatus"));

        Object statusValue = result.get("dlmsStatus");

        String dlmsStatus = statusValue instanceof Enum<?> enumValue
                ? enumValue.name()
                : statusValue == null ? null : statusValue.toString();

        meter.setDlmsStatus(dlmsStatus);

        meter.setDlmsMessage(
                (String) result.get("message"));

        Object duration = result.get("durationMs");
        if (duration instanceof Number number) {
            meter.setDurationMs(number.longValue());
        }

        meter.setRequestFrames(
                formatFrames(result.get("requestFrames")));

        meter.setResponseDetails(
                formatFrames(result.get("responseFrames")));

        meter.setUpdatedAt(LocalDateTime.now());
    }

    private String formatFrames(Object value) {
        if (!(value instanceof List<?> frames) || frames.isEmpty()) {
            return null;
        }

        return frames.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private ClockSyncMeter getMeter(Long id) {
        return repository.findById(id)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "Clock sync meter log not found: " + id));
    }

    @Transactional
    public void recordWorkerException(Long id, String message) {
        ClockSyncMeter meter = getMeter(id);

        LocalDateTime now = LocalDateTime.now();

        meter.setStatus("REVIEW");
        meter.setDlmsStatus("EXCEPTION");
        meter.setDlmsMessage(message);
        meter.setLastError(message);
        meter.setProcessedAt(now);
        meter.setUpdatedAt(now);

        // Do not release the claim: the outcome has not been established.
    }
}

