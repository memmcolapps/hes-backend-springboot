package com.memmcol.hes.repository;

import com.memmcol.hes.model.MeterRatioModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MeterRatioRepository extends JpaRepository<MeterRatioModel, Long> {
    // You can add custom queries here if needed
    Optional<MeterRatioModel> findByMeterSerial(String meterSerial);

}
