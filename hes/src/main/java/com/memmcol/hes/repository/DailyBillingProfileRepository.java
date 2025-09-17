package com.memmcol.hes.repository;

import com.memmcol.hes.entities.DailyBillingProfileEntity;
import com.memmcol.hes.entities.DailyBillingProfileId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DailyBillingProfileRepository
        extends JpaRepository<DailyBillingProfileEntity, DailyBillingProfileId> {

    List<DailyBillingProfileEntity> findByMeterSerialAndEntryTimestampBetween(
            String meterSerial, LocalDateTime start, LocalDateTime end);

}
