package com.memmcol.hes.application.port.in;


/**
 * 2. Port Interfaces (Application Layer Contracts)
 * Use case entry point: synchronize profile to "now".
 */
public interface ProfileSyncUseCase {
    void syncUpToNow(String model, String meterSerial, String profileObis, int batchSize);
}
