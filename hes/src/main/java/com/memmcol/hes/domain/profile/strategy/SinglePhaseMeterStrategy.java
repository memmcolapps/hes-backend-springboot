package com.memmcol.hes.domain.profile.strategy;

import com.memmcol.hes.dto.ProfileChannelTwoHouseholdDTO;
import org.springframework.stereotype.Component;

@Component
public class SinglePhaseMeterStrategy implements MeterProfileStrategy {

    public static final String SINGLE_PHASE_PREFIX = "62525";

    @Override
    public boolean supports(String meterSerial) {
        return meterSerial != null && meterSerial.startsWith(SINGLE_PHASE_PREFIX);
    }

    @Override
    public void normalize(ProfileChannelTwoHouseholdDTO dto) {
        if (dto == null) {
            return;
        }
        // Single phase meter: missing current_l2, current_l3, voltage_l2, voltage_l3, angles
        // Explicitly ensure missing phase 2/3 elements are normalized
        dto.setCurrentL2(null);
        dto.setCurrentL3(null);
        dto.setVoltageL2(null);
        dto.setVoltageL3(null);
        dto.setVoltAngleL1L2(null);
        dto.setVoltAngleL1L3(null);
    }
}
