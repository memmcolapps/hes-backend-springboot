package com.memmcol.hes.repository;

import com.memmcol.hes.model.SchedulerJobExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SchedulerJobExecutionRepository extends JpaRepository<SchedulerJobExecution, Long> {

    Optional<SchedulerJobExecution> findByFireInstanceId(String fireInstanceId);
}
