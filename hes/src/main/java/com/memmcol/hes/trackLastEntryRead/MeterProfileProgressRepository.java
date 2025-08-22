package com.memmcol.hes.trackLastEntryRead;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MeterProfileProgressRepository extends JpaRepository<MeterProfileProgress, Long> {
    Optional<MeterProfileProgress> findByMeterSerialAndProfileObis(String meterSerial, String profileObis);
}
