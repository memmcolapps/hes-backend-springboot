package com.memmcol.hes.repository;

import com.memmcol.hes.model.MetersConnectionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MetersConnectionEventRepository extends JpaRepository<MetersConnectionEvent, String > {
    @Query("""
        SELECT m 
        FROM MetersConnectionEvent m 
        WHERE m.updatedAt >= :fromTime
        ORDER BY m.updatedAt ASC
    """)
    List<MetersConnectionEvent> findRecentEvents(@Param("fromTime") LocalDateTime fromTime);

    /*✅ Purpose:
	•	Returns the latest connection status for each meter (ONLINE/OFFLINE).
	•	Based on connection_time.*/
    @Query("""
        SELECT e.meterNo, e.connectionType, e.connectionTime
        FROM MetersConnectionEvent e
        WHERE e.connectionTime = (
            SELECT MAX(e2.connectionTime)
            FROM MetersConnectionEvent e2
            WHERE e2.meterNo = e.meterNo
        )
    """)
    List<Object[]> findLatestConnectionEvents();
}
