package com.memmcol.hes.repository;

import com.memmcol.hes.entities.BillingHouseholdId;
import com.memmcol.hes.entities.DailyBillingEnergyHouseholdEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface DailyBillingEnergyHouseholdRepository
        extends JpaRepository<DailyBillingEnergyHouseholdEntity, BillingHouseholdId> {

    boolean existsByMeterSerialAndEntryTimestamp(
            String meterSerial,
            LocalDateTime entryTimestamp
    );
}
