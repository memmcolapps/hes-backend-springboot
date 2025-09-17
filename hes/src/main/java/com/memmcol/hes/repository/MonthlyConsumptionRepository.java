package com.memmcol.hes.repository;

import com.memmcol.hes.entities.MonthlyConsumptionEntity;
import com.memmcol.hes.entities.MonthlyConsumptionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface MonthlyConsumptionRepository
        extends JpaRepository<MonthlyConsumptionEntity, MonthlyConsumptionId> {

    Optional<MonthlyConsumptionEntity> findByMeterSerialAndMonthStart(String meterSerial, LocalDate monthStart);
}
