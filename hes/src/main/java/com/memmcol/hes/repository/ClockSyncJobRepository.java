package com.memmcol.hes.repository;


import com.memmcol.hes.entities.ClockSyncJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ClockSyncJobRepository
        extends JpaRepository<ClockSyncJob, Long> {

    boolean existsByStatus(String status);
}

