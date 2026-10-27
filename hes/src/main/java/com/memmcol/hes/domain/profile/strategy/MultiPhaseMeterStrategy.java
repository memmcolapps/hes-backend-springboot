package com.memmcol.hes.domain.profile.strategy;

import com.memmcol.hes.dto.ProfileChannelTwoHouseholdDTO;
import org.springframework.stereotype.Component;

@Component
public class MultiPhaseMeterStrategy implements MeterProfileStrategy {

    public static final String MULTI_PHASE_PREFIX = "62526";

    @Override
    public boolean supports(String meterSerial) {
        return meterSerial != null && meterSerial.startsWith(MULTI_PHASE_PREFIX);
    }

    @Override
    public void normalize(ProfileChannelTwoHouseholdDTO dto) {
        // Multi-phase meter: retains parsed phase fields as present in the payload
    }
}
