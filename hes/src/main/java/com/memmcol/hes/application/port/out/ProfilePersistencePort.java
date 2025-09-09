package com.memmcol.hes.application.port.out;

import com.memmcol.hes.domain.profile.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 2. Port Interfaces (Application Layer Contracts)
 */
public interface ProfilePersistencePort<T> {
    int saveBatch(String meterSerial, String profileObis, List<T> rows);

    ProfileTimestamp findLatestTimestamp(String meterSerial, String profileObis); // optional quick lookup

    ProfileSyncResult saveBatchAndAdvanceCursor(String meterSerial,
                                                String meterModel,
                                                String obis,
                                                List<T> rows,
                                                CapturePeriod capturePeriodSeconds,
                                                ProfileMetadataResult metadataResult);

    ProfileSyncResult saveBatchAndAdvanceCursor(String meterSerial,
                                                String meterModel,
                                                String obis,
                                                List<T> rows,
                                                CapturePeriod capturePeriodSeconds);
}
