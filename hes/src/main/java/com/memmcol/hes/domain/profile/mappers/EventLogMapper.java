package com.memmcol.hes.domain.profile.mappers;

import com.memmcol.hes.domain.profile.ProfileRowGeneric;
import com.memmcol.hes.dto.EventLogDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Configuration
@Slf4j
public class EventLogMapper {
    public List<EventLogDTO> toDTOs(List<ProfileRowGeneric> rawRows, String meterSerial) {
        return rawRows.stream().map(raw -> {
            // Convert LinkedHashMap values into ordered list
            List<Object> values = new ArrayList<>(raw.getValues().values());

            // Event time -> position 0
            LocalDateTime eventTime = (!values.isEmpty()) ? parseEventTime(values.get(0)) : null;
            assert eventTime != null;
            eventTime = eventTime.truncatedTo(ChronoUnit.SECONDS);

            // Event code -> position 1
            Integer eventCode = (values.size() > 1) ? parseEventCode(values.get(1)) : null;

            // Phase -> position 2 (optional)
            String phase = null;
            if (values.size() > 2 && values.get(2) != null && !values.get(2).toString().isBlank()) {
                phase = values.get(2).toString();
            }

            // Everything else goes into details (from position 3 onward)
            // details now holds your description (since you pivoted away from JSONB)
            String eventName = null; // Set later from lookup

            return EventLogDTO.builder()
                    .meterSerial(meterSerial)
                    .eventCode(eventCode)
                    .eventTime(eventTime)
                    .phase(phase)
                    .eventName(eventName) // ✅ avoid {}
                    .build();
        }).toList();
    }

    // Helper: parse event time safely
    private LocalDateTime parseEventTime(Object val) {
        switch (val) {
            case null -> {
                return null;
            }
            case LocalDateTime localDateTime -> {
                return localDateTime;
            }
            case Instant instant -> {
                return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
            }
            case String s -> {
                try {
                    return LocalDateTime.parse((String) val);
                } catch (Exception ignored) {
                }
            }
            default -> {
            }
        }
        return null;
    }

    // Helper: parse event code safely
    private Integer parseEventCode(Object rawCode) {
        switch (rawCode) {
            case null -> {
                return null;
            }
            case Number number -> {
                return number.intValue();
            }
            default -> {
            }
        }
        try {
            return Integer.parseInt(rawCode.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
