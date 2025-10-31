package com.memmcol.hes.repository;

import com.memmcol.hes.model.MetersConnectionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MetersConnectionEventRepository extends JpaRepository<MetersConnectionEvent, Long > {

    MetersConnectionEvent findByMeterNo(String meterNo);
    List<MetersConnectionEvent> getAllByMeterNo(String meterNo);

//    @Modifying
//    @Transactional
//    @Query("UPDATE MetersConnectionEvent m SET m.updatedAt = current_timestamp " +
//           "WHERE m.meterNo= :meterNo")
//    void updateMeterStatus(String meterNo);

    @Modifying
    @Transactional
    @Query("update MetersConnectionEvent m set m.updatedAt = current_timestamp " +
           "where m.updatedAt < :cutoffTime And m.meterNo= :meterNo")
    void markExpiredConnection(LocalDateTime cutoffTime,String meterNo);

    @Modifying
    @Transactional
    @Query("UPDATE MetersConnectionEvent m set m.updatedAt= :lastHeartbeatTime, m.updatedAt = current_timestamp " +
           "where m.meterNo= : meterNo")
    void updateHeartbeat(String meterNo, LocalDateTime lastHeartbeatTime);
}
