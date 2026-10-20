package com.memmcol.hes.domain.profile;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class MeterHealthGate {

    private final Map<String, HealthState> states = new ConcurrentHashMap<>();

    private int failureThreshold = 5;
    private long quarantineMillis = 30 * 60_000L;

    @Value("${hes.meter.health.failure-threshold:5}")
    public void setFailureThreshold(int value) {
        this.failureThreshold = Math.max(1, value);
    }

    @Value("${hes.meter.health.quarantine-minutes:30}")
    public void setQuarantineMinutes(int value) {
        this.quarantineMillis = Math.max(1, value) * 60_000L;
    }

    public boolean isQuarantined(String meterSerial) {
        HealthState state = states.get(meterSerial);
        return state != null && System.currentTimeMillis() < state.quarantinedUntilEpochMs;
    }

    public int quarantineCount() {
        long now = System.currentTimeMillis();
        int count = 0;
        for (HealthState state : states.values()) {
            if (now < state.quarantinedUntilEpochMs) {
                count++;
            }
        }
        return count;
    }

    public void recordSuccess(String meterSerial) {
        states.remove(meterSerial);
    }

    public void recordFailure(String meterSerial) {
        long now = System.currentTimeMillis();
        HealthState state = states.computeIfAbsent(meterSerial, k -> new HealthState());
        int failures = state.consecutiveFailures.incrementAndGet();

        if (failures >= failureThreshold && now >= state.quarantinedUntilEpochMs) {
            state.quarantinedUntilEpochMs = now + quarantineMillis;
            log.warn("Meter {} quarantined for {} min after {} consecutive read failure(s)",
                    meterSerial, quarantineMillis / 60_000L, failures);
        }
    }

    private static final class HealthState {
        private final AtomicInteger consecutiveFailures = new AtomicInteger();
        private volatile long quarantinedUntilEpochMs;
    }
}
