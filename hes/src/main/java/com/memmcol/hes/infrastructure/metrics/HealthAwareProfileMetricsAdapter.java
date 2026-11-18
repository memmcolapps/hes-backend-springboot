package com.memmcol.hes.infrastructure.metrics;

import com.memmcol.hes.application.port.out.ProfileMetricsPort;
import com.memmcol.hes.domain.profile.MeterHealthGate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
@RequiredArgsConstructor
public class HealthAwareProfileMetricsAdapter implements ProfileMetricsPort {

    private final MicrometerProfileMetricsAdapter delegate;
    private final MeterHealthGate meterHealthGate;

    @Override
    public void recordBatch(String meterSerial, String profileObis, int rowsSaved, long millis) {
        delegate.recordBatch(meterSerial, profileObis, rowsSaved, millis);
        meterHealthGate.recordSuccess(meterSerial);
    }

    @Override
    public void recordFailure(String meterSerial, String profileObis, String cause) {
        delegate.recordFailure(meterSerial, profileObis, cause);
        meterHealthGate.recordFailure(meterSerial);
    }

    @Override
    public void recordRecovery(String meterSerial, String profileObis, int salvaged) {
        delegate.recordRecovery(meterSerial, profileObis, salvaged);
        meterHealthGate.recordSuccess(meterSerial);
    }
}
