package com.memmcol.hes.repository;

import com.memmcol.hes.domain.clock.MeterSerialProvider;
import com.memmcol.hes.dto.MeterDTO;
import com.memmcol.hes.model.MetersConnectionEvent;
import com.memmcol.hes.model.MetersEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
       WHERE m.meterNumber IN :meterNumbers
       """)
    List<MeterDTO> findMeterDetailsByMeterNumberIn(@Param("meterNumbers") List<String> meterNumbers);

    /*✅ Purpose:
	•	Retrieves all meter numbers with their corresponding models.
	•	Joins meters with smart_meter_info.*/
    @Query("""
        SELECT m.meterNumber, s.meterModel
        FROM MetersEntity m
        JOIN SmartMeterInfo s ON m.id = s.meter.id
    """)
    List<Object[]> findAllMeterModels();

    @Query("""
        SELECT m.meterNumber
        FROM MetersEntity m
        JOIN SmartMeterInfo s ON m.id = s.meter.id
        WHERE m.meterNumber IS NOT NULL
    """)
    List<String> findAllRegisteredMeterNumbers();


    @Query("""
    SELECT m.id AS id, m.meterNumber AS meterNumber
    FROM MetersEntity m
    JOIN SmartMeterInfo s ON m.id = s.meter.id
    WHERE m.meterNumber IS NOT NULL
    ORDER BY m.id ASC
""")
    List<MeterSerialProvider> findFirstRegisteredMeterBatch(
            Pageable pageable
    );

    @Query("""
    SELECT m.id AS id, m.meterNumber AS meterNumber
    FROM MetersEntity m
    JOIN SmartMeterInfo s ON m.id = s.meter.id
    WHERE m.meterNumber IS NOT NULL
      AND m.id > :lastId
    ORDER BY m.id ASC
""")
    List<MeterSerialProvider> findRegisteredMetersAfter(
            @Param("lastId") UUID lastId,
            Pageable pageable
    );

    @Query("""
    SELECT DISTINCT m.meterNumber
    FROM MetersEntity m
    JOIN SmartMeterInfo s ON m.id = s.meter.id
    WHERE m.meterNumber IN :meterNumbers
      AND m.meterNumber IS NOT NULL
""")
    List<String> findExistingRegisteredMeterNumbers(
            @Param("meterNumbers") Collection<String> meterNumbers
    );


}
