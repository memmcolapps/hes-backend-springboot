package com.memmcol.hes.repository;

import com.memmcol.hes.dto.HouseholdRechargeTokenEventDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class HouseholdRechargeTokenEventCustomRepository {

    private final EntityManager entityManager;

    public List<Object[]> findExistingEvents(String meterSerial, List<HouseholdRechargeTokenEventDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return List.of();
        }
        String values = dtos.stream()
                .map(dto -> "(" + dto.getEventCode() + ", '" + dto.getEventTime() + "')")
                .collect(Collectors.joining(", "));

        String sql = """
            SELECT event_code, event_time
            FROM household_recharge_token_event
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
