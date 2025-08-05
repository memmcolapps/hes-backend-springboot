package com.memmcol.hes.application.port.out;

import com.memmcol.hes.domain.profile.CapturePeriod;
import com.memmcol.hes.domain.profile.ProfileRow;
import com.memmcol.hes.domain.profile.ProfileSyncResult;
import com.memmcol.hes.domain.profile.ProfileTimestamp;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 2. Port Interfaces (Application Layer Contracts)
 */
public interface ProfilePersistencePort {
    int saveBatch(String meterSerial, String profileObis, List<ProfileRow> rows);

    ProfileTimestamp findLatestTimestamp(String meterSerial, String profileObis); // optional quick lookup

    ProfileSyncResult saveBatchAndAdvanceCursor(String meterSerial,
                                                String meterModel,
                                                String obis,
                                                List<ProfileRow> rows,
                                                CapturePeriod capturePeriodSeconds,
                                                Map<String, Double> scalers);
}
