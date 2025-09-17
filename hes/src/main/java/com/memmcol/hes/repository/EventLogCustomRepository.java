package com.memmcol.hes.repository;

import com.memmcol.hes.dto.EventLogDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class EventLogCustomRepository {

    private final EntityManager entityManager;

    public List<Object[]> findExistingEvents(String meterSerial, List<EventLogDTO> dtos) {
        if (dtos.isEmpty()) return List.of();

        // Build tuple list: (event_code, event_time)
        String values = dtos.stream()
                .map(dto -> "(" + dto.getEventCode() + ", '" + dto.getEventTime() + "')")
                .collect(Collectors.joining(", "));

        String sql = """
            SELECT event_code, event_time
            FROM event_log
            WHERE meter_serial = :meterSerial
            AND (event_code, event_time) IN (%s)
            """.formatted(values);

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("meterSerial", meterSerial);

        @SuppressWarnings("unchecked")
        List<Object[]> result = query.getResultList();
        return result;
    }
}

