package com.memmcol.hes.domain.profile.mappers;

import com.memmcol.hes.domain.profile.ProfileRowGeneric;
import com.memmcol.hes.dto.HouseholdControlEventDTO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps DLMS rows for household control event log (time, code, reason of operation).
 */
@Component
public class HouseholdControlEventMapper {

    public List<HouseholdControlEventDTO> toDTOs(List<ProfileRowGeneric> rawRows,
                                                 String meterSerial,
                                                 String meterModel,
                                                 String profileObis) {
        return rawRows.stream()
                .map(raw -> toDto(raw, meterSerial, meterModel, profileObis))
                .filter(dto -> dto.getEventTime() != null && dto.getEventCode() != null)
                .toList();
    }

    private HouseholdControlEventDTO toDto(ProfileRowGeneric raw,
                                           String meterSerial,
                                           String meterModel,
                                           String profileObis) {
        List<Object> values = new ArrayList<>(raw.getValues().values());
        LocalDateTime eventTime = !values.isEmpty() ? EventRowValueParser.parseEventTime(values.get(0)) : null;
        if (eventTime != null) {
            eventTime = eventTime.truncatedTo(ChronoUnit.SECONDS);
        }
        Integer eventCode = values.size() > 1 ? EventRowValueParser.parseEventCode(values.get(1)) : null;
        Object reasonRaw = values.size() > 2 ? values.get(2) : null;

        return HouseholdControlEventDTO.builder()
                .meterSerial(meterSerial)
                .meterModel(meterModel)
                .profileObis(profileObis)
                .eventCode(eventCode)
                .eventTime(eventTime)
                .reasonOfOperationRaw(reasonRaw)
                .build();
    }
}
