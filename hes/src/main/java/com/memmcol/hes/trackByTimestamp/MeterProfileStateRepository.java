package com.memmcol.hes.trackByTimestamp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface MeterProfileStateRepository extends JpaRepository<MeterProfileState, Long> {

    Optional<MeterProfileState> findByMeterSerialAndProfileObis(String meterSerial, String profileObis);

    @Query("select s.lastTimestamp from MeterProfileState s " +
            "where s.meterSerial = :serial and s.profileObis = :obis")
    Optional<LocalDateTime> findLastTimestamp(String serial, String obis);

    @Modifying
    @Query("UPDATE MeterProfileState m SET m.lastTimestamp = :ts, m.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE m.meterSerial = :serial AND m.profileObis = :obis")
    int updateLastTimestamp(@Param("serial") String serial,
                            @Param("obis") String obis,
                            @Param("ts") LocalDateTime timestamp);

//    @Modifying
//    @Query("UPDATE MeterProfileState m SET m.capturePeriodSec = :cap, m.updatedAt = CURRENT_TIMESTAMP " +
//            "WHERE m.meterSerial = :serial AND m.profileObis = :obis")
//    int updateCapturePeriod(@Param("serial") String serial,
//                            @Param("obis") String obis,
//                            @Param("cap") Integer capturePeriodSec);

    @Modifying
    @Query("UPDATE MeterProfileState m SET m.lastTimestamp = :timestamp, m.capturePeriodSec = :capturePeriodSec, m.updatedAt = CURRENT_TIMESTAMP WHERE m.meterSerial = :serial AND m.profileObis = :obis")
    int updateTimestampAndCapturePeriod(@Param("serial") String serial,
                                        @Param("obis") String obis,
                                        @Param("timestamp") LocalDateTime timestamp,
                                        @Param("capturePeriodSec") Integer capturePeriodSec);

    @Query("select s.capturePeriodSec from MeterProfileState s " +
            "where s.meterSerial = :serial and s.profileObis = :obis")
    Optional<Integer> findCapturePeriodSec(String serial, String obis);

    @Modifying
    @Query("""
       update MeterProfileState s
          set s.capturePeriodSec = :cp,
              s.updatedAt = CURRENT_TIMESTAMP
        where s.meterSerial = :serial
          and s.profileObis = :obis
    """)
    int updateCapturePeriod(String serial, String obis, Integer cp);
}
