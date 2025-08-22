package com.memmcol.hes.trackByTimestamp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MeterProfileTimestampProgressRepository extends JpaRepository<MeterProfileTimestampProgress, Long> {
    Optional<MeterProfileTimestampProgress> findByMeterSerialAndProfileObis(String meterSerial, String profileObis);

    @Query("SELECT r.entryTimestamp FROM ProfileChannel2Reading r WHERE r.meterSerial = :serial AND r.entryTimestamp IN :timestamps")
    List<LocalDateTime> findExistingTimestamps(@Param("serial") String serial, @Param("timestamps") List<LocalDateTime> timestamps);

    @Query("SELECT MAX(p.entryTimestamp) FROM ProfileChannel2Reading p WHERE p.meterSerial = :serial")
    LocalDateTime findLatestTimestamp(@Param("serial") String serial);

}
