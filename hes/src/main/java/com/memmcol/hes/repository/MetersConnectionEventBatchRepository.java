package com.memmcol.hes.repository;

import com.memmcol.hes.dto.MeterUpdateDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class MetersConnectionEventBatchRepository {

    private final JdbcTemplate jdbcTemplate;

    public MetersConnectionEventBatchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Batch UPSERT for meter connection state updates.
     * - One row per meter (state table, not event history)
     * - ONLINE updates only online_time
     * - OFFLINE updates only offline_time
     */
    public void batchUpsert(List<MeterUpdateDTO> updates) {

        if (updates == null || updates.isEmpty()) {
            return;
        }

        String sql = """
                    INSERT INTO meters_connection_event (
                          meter_no,
                          connection_type,
                          online_time,
                          offline_time,
                          updated_at
                      )
                      VALUES (?, ?, ?, ?, ?)
                      ON CONFLICT (meter_no)
                      DO UPDATE SET
                          connection_type = EXCLUDED.connection_type,
        
                          online_time = CASE
                              WHEN EXCLUDED.connection_type = 'ONLINE'
                              THEN EXCLUDED.online_time
                              ELSE meters_connection_event.online_time
                          END,
        
                          offline_time = CASE
                              WHEN EXCLUDED.connection_type = 'OFFLINE'
                              THEN EXCLUDED.offline_time
                              ELSE meters_connection_event.offline_time
                          END,
        
                          updated_at = EXCLUDED.updated_at
                      WHERE
                          meters_connection_event.connection_type IS DISTINCT FROM EXCLUDED.connection_type
                          OR meters_connection_event.online_time IS DISTINCT FROM EXCLUDED.online_time
                          OR meters_connection_event.offline_time IS DISTINCT FROM EXCLUDED.offline_time
                """;

        jdbcTemplate.batchUpdate(sql, updates, 500, (ps, u) -> {
            ps.setString(1, u.getMeterNo());
            ps.setString(2, u.getStatus());

            // unified timestamp from heartbeat event
            Timestamp ts = Timestamp.valueOf(u.getTimestamp());

            // we still pass both; SQL decides which one applies
            ps.setTimestamp(3, ts);
            ps.setTimestamp(4, ts);
            ps.setTimestamp(5, ts);
        });
    }
}