package com.memmcol.hes.repository;

import com.memmcol.hes.model.ProfileChannel2Reading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProfileChannel2Repository extends JpaRepository<ProfileChannel2Reading, Long> {

    Optional<ProfileChannel2Reading> findTopByMeterSerialOrderByEntryIndexDesc(String meterSerial);

    Optional<ProfileChannel2Reading> findTopByMeterSerialOrderByEntryTimestampDesc(String meterSerial);

    boolean existsByMeterSerialAndEntryIndex(String meterSerial, Long entryIndex);

    @Query("""
    SELECT p.entryIndex
    FROM ProfileChannel2Reading p
    WHERE p.meterSerial = :serial
      AND p.entryTimestamp IN :timestamps
      AND p.entryIndex IN :entryIndexes
""")
    List<Long> findExistingIndexesWithTimestamps(
            @Param("serial") String serial,
            @Param("entryIndexes") List<Integer> entryIndexes,
            @Param("timestamps") List<LocalDateTime> timestamps
    );

}
