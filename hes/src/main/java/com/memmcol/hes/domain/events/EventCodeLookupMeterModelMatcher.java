package com.memmcol.hes.domain.events;

import java.util.Arrays;

/**
 * Matches a meter model against {@code event_code_lookup.meter_model} (single value or comma-separated list).
 */
public final class EventCodeLookupMeterModelMatcher {

    private EventCodeLookupMeterModelMatcher() {
    }

    /**
     * @param lookupMeterModels null/blank = applies to all models
     */
    public static boolean applies(String lookupMeterModels, String meterModel) {
        if (meterModel == null || meterModel.isBlank()) {
            return true;
        }
        if (lookupMeterModels == null || lookupMeterModels.isBlank()) {
            return true;
        }
        return Arrays.stream(lookupMeterModels.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .anyMatch(entry -> entry.equalsIgnoreCase(meterModel.trim()));
    }
}
