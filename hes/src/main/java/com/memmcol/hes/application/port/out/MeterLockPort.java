package com.memmcol.hes.application.port.out;

/**
 * 2. Port Interfaces (Application Layer Contracts)
 * Per-meter mutual exclusion to protect a DLMS session.
 */
public interface MeterLockPort {
    <T> T withExclusive(String meterSerial, java.util.concurrent.Callable<T> action) throws Exception;

    boolean tryExclusive(String meterSerial, Runnable action, long millis);
}