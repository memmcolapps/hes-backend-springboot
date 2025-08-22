package com.memmcol.hes.application.port.out;

/**
 * 2. Port Interfaces (Application Layer Contracts)
 */
public interface ProfileMetricsPort {
    void recordBatch(String meterSerial, String profileObis, int rowsSaved, long millis);

    void recordFailure(String meterSerial, String profileObis, String cause);

    void recordRecovery(String meterSerial, String profileObis, int salvaged);
}
