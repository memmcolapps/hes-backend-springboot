package com.memmcol.hes.domain.profile.strategy;

import com.memmcol.hes.dto.ProfileChannelTwoHouseholdDTO;

public interface MeterProfileStrategy {
    boolean supports(String meterSerial);
    void normalize(ProfileChannelTwoHouseholdDTO dto);
}
