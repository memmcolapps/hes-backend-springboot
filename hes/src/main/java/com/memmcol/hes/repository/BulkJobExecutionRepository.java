package com.memmcol.hes.repository;

import com.memmcol.hes.entities.BulkJobExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BulkJobExecutionRepository extends JpaRepository<BulkJobExecution, Long> {
}
