package com.memmcol.hes.repository;

import com.memmcol.hes.entities.BulkJobMeterLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BulkJobMeterLogRepository extends JpaRepository<BulkJobMeterLog, Long> {
    List<BulkJobMeterLog> findByJobExecutionIdAndStatus(Long jobExecutionId, String status);
    Page<BulkJobMeterLog> findByJobExecutionId(Long jobExecutionId, Pageable pageable);
    long countByJobExecutionIdAndStatus(Long jobExecutionId, String status);
}
