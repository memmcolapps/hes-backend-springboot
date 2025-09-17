package com.memmcol.hes.repository;

import com.memmcol.hes.model.Meter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface MeterRepository extends JpaRepository<Meter, UUID> {

    // Fetch only meters whose serial numbers are in the given set
    @Query("SELECT m FROM Meter m WHERE m.meterNumber IN :activeSerials")
    List<Meter> findByMeterNumbersIn(@Param("activeSerials") Set<String> activeSerials);
}