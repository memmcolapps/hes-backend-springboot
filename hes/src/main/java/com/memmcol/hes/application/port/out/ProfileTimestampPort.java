package com.memmcol.hes.application.port.out;

import java.time.LocalDateTime;

/**
 * Resolves the last known timestamp of a meter profile.
 */
public interface ProfileTimestampPort {

    /**
     * Resolves the last timestamp for a given meter & profile OBIS.
     * Checks cache → DB → meter, and falls back to a default value if not found.
     *
     * @param meterSerial Meter serial number.
     * @param profileObis Profile OBIS code.
     * @return The last known timestamp (or fallback timestamp).
     */
    LocalDateTime resolveLastTimestamp(String meterSerial, String profileObis);
}