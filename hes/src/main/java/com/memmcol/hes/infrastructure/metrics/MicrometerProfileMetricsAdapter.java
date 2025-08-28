package com.memmcol.hes.infrastructure.metrics;

import com.memmcol.hes.application.port.out.ProfileMetricsPort;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 4.5 Metrics Adapter (Micrometer)
 */
@Component
@RequiredArgsConstructor
public class MicrometerProfileMetricsAdapter implements ProfileMetricsPort {

    private final MeterRegistry registry;

    @Override
    public void recordBatch(String meterSerial, String profileObis, int rowsSaved, long millis) {
        registry.counter("hes.profile.rows.saved",
                        "meter", meterSerial, "profile", profileObis)
                .increment(rowsSaved);

        registry.timer("hes.profile.batch.duration",
                        "meter", meterSerial, "profile", profileObis)
                .record(java.time.Duration.ofMillis(millis));
    }

    @Override
    public void recordFailure(String meterSerial, String profileObis, String cause) {
        registry.counter("hes.profile.failures",
                        "meter", meterSerial, "profile", profileObis, "cause", cause)
                .increment();
    }

    @Override
    public void recordRecovery(String meterSerial, String profileObis, int salvaged) {
        registry.counter("hes.profile.recovery.rows",
                        "meter", meterSerial, "profile", profileObis)
                .increment(salvaged);
    }
}
