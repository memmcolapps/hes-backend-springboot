package com.memmcol.hes.repository;

import com.memmcol.hes.entities.EventLog;
import com.memmcol.hes.gridflex.records.DashboardSummaryResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Repository
public interface EventLogRepository extends JpaRepository<EventLog, Long> {

}
