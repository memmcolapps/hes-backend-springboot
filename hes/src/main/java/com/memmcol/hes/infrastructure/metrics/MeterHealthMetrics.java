package com.memmcol.hes.infrastructure.metrics;

import com.memmcol.hes.domain.profile.MeterHealthGate;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MeterHealthMetrics implements MeterBinder {

    private final MeterHealthGate meterHealthGate;

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("hes.meters.quarantined", meterHealthGate, MeterHealthGate::quarantineCount)
                .description("Number of meters currently quarantined after repeated read failures")
                .register(registry);
    }
}
