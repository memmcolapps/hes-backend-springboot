package com.memmcol.hes.repository;

import com.memmcol.hes.dto.MeterDTO;
import com.memmcol.hes.model.MetersEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface MeterRepository extends JpaRepository<MetersEntity, UUID> {

    // Fetch only meters whose serial numbers are in the given set
    @Query("""
       SELECT new com.memmcol.hes.dto.MeterDTO(
           m.meterNumber,
           s.meterModel,
           m.meterClass,
           false,
           m.createdAt
           )
       FROM MetersEntity m
       JOIN m.smartMeterInfo s
       WHERE m.meterNumber = :meterNumber
       """)
    Optional<MeterDTO> findMeterDetailsByMeterNumber(@Param("meterNumber") String meterNumber);

    /*✅ Purpose:
	•	Retrieves all meter numbers with their corresponding models.
	•	Joins meters with smart_meter_info.*/
    @Query("""
        SELECT m.meterNumber, s.meterModel
        FROM MetersEntity m
        JOIN SmartMeterInfo s ON m.id = s.meter.id
    """)
    List<Object[]> findAllMeterModels();
}