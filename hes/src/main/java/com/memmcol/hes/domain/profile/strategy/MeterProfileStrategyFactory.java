package com.memmcol.hes.domain.profile.strategy;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MeterProfileStrategyFactory {

    private final List<MeterProfileStrategy> strategies;
    private final MeterProfileStrategy defaultStrategy = new MultiPhaseMeterStrategy();

    public MeterProfileStrategyFactory(List<MeterProfileStrategy> strategies) {
        this.strategies = strategies;
    }

    public MeterProfileStrategy getStrategy(String meterSerial) {
        if (meterSerial == null || strategies == null) {
            return defaultStrategy;
        }

        return strategies.stream()
                .filter(strategy -> strategy.supports(meterSerial))
                .findFirst()
                .orElse(defaultStrategy);
    }
}
