package com.memmcol.hes.repository;

import com.memmcol.hes.model.SchedulerJobInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface SchedulerRepository extends JpaRepository<SchedulerJobInfo, Long> {
    SchedulerJobInfo findByJobName(String jobName);
    Optional<SchedulerJobInfo> findByJobNameAndJobGroup(String jobName, String jobGroup);
}