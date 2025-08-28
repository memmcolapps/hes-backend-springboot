package com.memmcol.hes.application.port.out;

import com.memmcol.hes.domain.profile.CapturePeriod;
import com.memmcol.hes.domain.profile.ProfileState;
import com.memmcol.hes.domain.profile.ProfileTimestamp;

/**
 * 2. Port Interfaces (Application Layer Contracts)
 */
public interface ProfileStatePort {
    ProfileState loadState(String meterSerial, String profileObis);

    void upsertState(String meterSerial, String profileObis,
                     ProfileTimestamp lastTs, CapturePeriod capturePeriod);
}
