package com.memmcol.hes.application.port.out;

import com.memmcol.hes.domain.profile.ProfileMetadataResult;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 2. Port Interfaces (Application Layer Contracts)
 */
public interface ProfileDataReaderPort<T> {
    List<T> readRange(String model, String meterSerial, String profileObis, ProfileMetadataResult metadataResult,
                               LocalDateTime from, LocalDateTime to) throws ProfileReadException;

}
