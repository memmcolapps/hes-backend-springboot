package com.memmcol.hes.repository;

import com.memmcol.hes.entities.MonthlyBillingEntity;
import com.memmcol.hes.entities.MonthlyBillingId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface MonthlyBillingRepository
        extends JpaRepository<MonthlyBillingEntity, MonthlyBillingId> {

    boolean existsByMeterSerialAndEntryTimestamp(String meterSerial, LocalDateTime entryTimestamp);

    Optional<MonthlyBillingEntity> findByMeterSerialAndEntryTimestamp(String meterSerial, LocalDateTime entryTimestamp);
}

