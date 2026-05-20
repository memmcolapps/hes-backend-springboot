package com.memmcol.hes.domain.events;

/**
 * Identifies a schedulable event profile for OBIS resolution and logging.
 * Keys align with {@code hes.events.profiles.<key>} configuration entries.
 */
public enum EventScheduleProfile {
    RECHARGE_TOKEN("recharge-token"),
    MANAGEMENT_TOKEN("management-token"),
    FRAUD_EVENT("fraud-event"),
    CONTROL_EVENT("control-event");

    private final String configKey;

    EventScheduleProfile(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }
}
