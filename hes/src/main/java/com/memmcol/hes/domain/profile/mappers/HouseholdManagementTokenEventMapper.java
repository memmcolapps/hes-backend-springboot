package com.memmcol.hes.domain.profile.mappers;

import com.memmcol.hes.domain.profile.ProfileRowGeneric;
import com.memmcol.hes.dto.HouseholdManagementTokenEventDTO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps DLMS rows for household management token log (time, code, token type, token).
 */
@Component
public class HouseholdManagementTokenEventMapper {

    public List<HouseholdManagementTokenEventDTO> toDTOs(List<ProfileRowGeneric> rawRows,
                                                         String meterSerial,
                                                         String meterModel,
                                                         String profileObis) {
        return rawRows.stream()
                .map(raw -> toDto(raw, meterSerial, meterModel, profileObis))
                .filter(dto -> dto.getEventTime() != null && dto.getEventCode() != null)
                .toList();
    }

    private HouseholdManagementTokenEventDTO toDto(ProfileRowGeneric raw,
                                                   String meterSerial,
                                                   String meterModel,
                                                   String profileObis) {
        List<Object> values = new ArrayList<>(raw.getValues().values());
        LocalDateTime eventTime = !values.isEmpty() ? EventRowValueParser.parseEventTime(values.get(0)) : null;
        if (eventTime != null) {
            eventTime = eventTime.truncatedTo(ChronoUnit.SECONDS);
        }
        Integer eventCode = values.size() > 1 ? EventRowValueParser.parseEventCode(values.get(1)) : null;
        String tokenType = values.size() > 2 ? EventRowValueParser.parseStringValue(values.get(2)) : null;
        String token = values.size() > 3 ? EventRowValueParser.parseStringValue(values.get(3)) : null;

        return HouseholdManagementTokenEventDTO.builder()
                .meterSerial(meterSerial)
                .meterModel(meterModel)
                .profileObis(profileObis)
                .eventCode(eventCode)
                .eventTime(eventTime)
                .manageTokenType(tokenType)
                .manageToken(token)
                .build();
    }
}
