package com.memmcol.hes.domain.clock;


import com.memmcol.hes.entities.ClockSyncMeter;
import com.memmcol.hes.repository.ClockSyncMeterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ClockSyncBatchService {

    private static final int CLAIM_BATCH_SIZE = 500;

    private final ClockSyncMeterRepository repository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public List<ClockSyncMeter> claimPendingBatch(Long jobId) {
        List<ClockSyncMeter> batch = repository.lockPendingBatch(
                jobId, PageRequest.of(0, 100));

        LocalDateTime now = LocalDateTime.now();

        for (ClockSyncMeter meter : batch) {
            meter.setStatus("PROCESSING");
            meter.setAttemptCount(meter.getAttemptCount() + 1);
            meter.setAttemptedAt(now);
            meter.setUpdatedAt(now);
        }

        repository.saveAllAndFlush(batch);
        return batch;
    }

    /**
     * Claims only meters that are not already claimed by another job.
     * The returned set contains meters claimed by this job.
     */
    @Transactional
    public Set<String> tryClaimMeters(
            Long jobId, Collection<String> meterNumbers) {

        if (meterNumbers == null || meterNumbers.isEmpty()) {
            return Collections.emptySet();
        }

        List<String> meters = meterNumbers.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();

        for (int start = 0; start < meters.size();
             start += CLAIM_BATCH_SIZE) {

            List<String> batch = meters.subList(
                    start,
                    Math.min(start + CLAIM_BATCH_SIZE, meters.size()));

//            jdbcTemplate.batchUpdate(
//                    """
//                    INSERT INTO clock_sync_meter_claim
//                        (meter_number, job_id, claimed_at)
//                    VALUES (?, ?, CURRENT_TIMESTAMP)
//                    ON CONFLICT (meter_number) DO NOTHING
//                    """,
//                    batch,
//                    batch.size(),
//                    (ps, meterNumber) -> {
//                        ps.setString(1, meterNumber);
//                        ps.setLong(2, jobId);
//                    });


            jdbcTemplate.batchUpdate(
                    """
                    INSERT INTO clock_sync_meter_claim
                        (meter_number, job_id, claimed_at)
                    VALUES (?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT (meter_number) DO UPDATE
                    SET job_id = EXCLUDED.job_id,
                        claimed_at = CURRENT_TIMESTAMP
                    WHERE EXISTS (
                        SELECT 1
                        FROM clock_sync_job previous_job
                        JOIN clock_sync_meter previous_meter
                          ON previous_meter.job_id = previous_job.id
                        WHERE previous_job.id = clock_sync_meter_claim.job_id
                          AND previous_meter.meter_number = EXCLUDED.meter_number
                          AND previous_meter.status = 'REVIEW'
                          AND previous_job.status IN (
                              'COMPLETED',
                              'COMPLETED_WITH_ERRORS',
                              'FAILED'
                          )
                    )
                    """,
                    batch,
                    batch.size(),
                    (ps, meterNumber) -> {
                        ps.setString(1, meterNumber);
                        ps.setLong(2, jobId);
                    });

        }

        Set<String> claimed = new HashSet<>();

        for (int start = 0; start < meters.size();
             start += CLAIM_BATCH_SIZE) {

            List<String> batch = meters.subList(
                    start,
                    Math.min(start + CLAIM_BATCH_SIZE, meters.size()));

            String placeholders = String.join(
                    ",", Collections.nCopies(batch.size(), "?"));

            List<String> found = jdbcTemplate.queryForList(
                    """
                    SELECT meter_number
                    FROM clock_sync_meter_claim
                    WHERE job_id = ?
                      AND meter_number IN (%s)
                    """.formatted(placeholders),
                    String.class,
                    combineJobIdAndMeters(jobId, batch));

            claimed.addAll(found);
        }

        return claimed;
    }

    /**
     * Release a claim only after a definite terminal result is saved.
     */
    @Transactional
    public void releaseClaim(String meterNumber, Long jobId) {
        jdbcTemplate.update(
                """
                DELETE FROM clock_sync_meter_claim
                WHERE meter_number = ? AND job_id = ?
                """,
                meterNumber,
                jobId);
    }

    private Object[] combineJobIdAndMeters(
            Long jobId, List<String> meters) {

        Object[] args = new Object[meters.size() + 1];
        args[0] = jobId;

        for (int i = 0; i < meters.size(); i++) {
            args[i + 1] = meters.get(i);
        }

        return args;
    }

}
//@Service
//@RequiredArgsConstructor
//public class ClockSyncBatchService {
//
//    private final ClockSyncMeterRepository repository;
//
//    @Transactional
//    public List<ClockSyncMeter> claimPendingBatch(Long jobId) {
//        List<ClockSyncMeter> batch = repository.lockPendingBatch(
//                jobId, PageRequest.of(0, 100));
//
//        for (ClockSyncMeter meter : batch) {
//            meter.setStatus("PROCESSING");
//            meter.setAttemptCount(meter.getAttemptCount() + 1);
//            meter.setAttemptedAt(LocalDateTime.now());
//            meter.setUpdatedAt(LocalDateTime.now());
//        }
//
//        repository.saveAllAndFlush(batch);
//        return batch;
//    }
//}

