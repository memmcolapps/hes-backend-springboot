package com.memmcol.hes.application.port.out;

import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

/**
 * 2. Port Interfaces (Application Layer Contracts)
 * Capture period retrieval (e.g. from meter or cached DB).
 */
public interface CapturePeriodPort {
    int resolveCapturePeriodSeconds(String meterSerial, String profileObis);

    // optional: refresh from meter
    int refreshCapturePeriodSeconds(String meterSerial, String profileObis);
}
