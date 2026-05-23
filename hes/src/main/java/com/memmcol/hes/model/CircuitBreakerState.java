package com.memmcol.hes.model;

public class CircuitBreakerState {
    public CircuitState state = CircuitState.CLOSED;

    public int failureCount = 0;

    public long openUntil = 0;
}
