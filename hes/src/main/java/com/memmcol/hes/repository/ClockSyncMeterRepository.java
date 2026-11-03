package com.memmcol.hes.repository;


import com.memmcol.hes.entities.ClockSyncMeter;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface ClockSyncMeterRepository
        extends JpaRepository<ClockSyncMeter, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT m
        FROM ClockSyncMeter m
        WHERE m.jobId = :jobId
          AND m.status = 'PENDING'
        ORDER BY m.id ASC
    """)
    List<ClockSyncMeter> lockPendingBatch(
            @Param("jobId") Long jobId,
            Pageable pageable
    );

    long countByJobIdAndStatus(Long jobId, String status);

    long countByJobId(Long jobId);

    Page<ClockSyncMeter> findByJobIdAndStatus(
            long jobId,
            String status,
            Pageable pageable
    );

    Page<ClockSyncMeter> findByJobId(Long jobId, Pageable pageable);

    List<ClockSyncMeter> findByMeterNumberInAndStatusIn(
            Collection<String> meterNumbers,
            Collection<String> statuses
    );


    @Query("""
    SELECT DISTINCT m.meterNumber
    FROM ClockSyncMeter m
    JOIN ClockSyncJob j ON j.id = m.jobId
    WHERE m.meterNumber IN :meterNumbers
      AND (
          m.status IN ('PENDING', 'PROCESSING', 'SUCCESS')
          OR (
              m.status = 'REVIEW'
              AND j.status NOT IN (
                  'COMPLETED',
                  'COMPLETED_WITH_ERRORS',
                  'FAILED'
              )
          )
      )
    """)
    Set<String> findBlockedMeterNumbersForRegistration(
            @Param("meterNumbers") Collection<String> meterNumbers);


    @Query("""
    SELECT DISTINCT cm.meterNumber
    FROM ClockSyncMeter cm
    WHERE cm.meterNumber IN :meterNumbers
      AND cm.status IN :statuses
""")
    Set<String> findBlockedMeterNumbers(
            @Param("meterNumbers") Collection<String> meterNumbers,
            @Param("statuses") Collection<String> statuses
    );

    @Query("""
    SELECT cm.meterNumber
    FROM ClockSyncMeter cm
    WHERE cm.jobId = :jobId
      AND cm.meterNumber IN :meterNumbers
""")
    Set<String> findExistingMeterNumbersForJob(
            @Param("jobId") Long jobId,
            @Param("meterNumbers") Collection<String> meterNumbers);
}

