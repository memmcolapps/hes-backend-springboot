package com.memmcol.hes.repository;

import com.memmcol.hes.entities.BillingHouseholdId;
import com.memmcol.hes.entities.DailyBillingEnergyHouseholdEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface DailyBillingEnergyHouseholdRepository
        extends JpaRepository<DailyBillingEnergyHouseholdEntity, BillingHouseholdId> {

    boolean existsByMeterSerialAndEntryTimestamp(
            String meterSerial,
            LocalDateTime entryTimestamp
    );
    @Query("""
        SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END
        FROM DailyBillingEnergyHouseholdEntity e
        WHERE e.meterSerial = :meterSerial
          AND e.entryTimestamp >= :startOfDay
          AND e.entryTimestamp < :startOfNextDay
        """)
    boolean existsByMeterSerialAndEntryDate(
            @Param("meterSerial") String meterSerial,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("startOfNextDay") LocalDateTime startOfNextDay
    );
}
