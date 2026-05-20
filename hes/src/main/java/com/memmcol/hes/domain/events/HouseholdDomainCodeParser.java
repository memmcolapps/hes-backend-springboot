package com.memmcol.hes.domain.events;

import com.memmcol.hes.domain.profile.mappers.EventRowValueParser;

/**
 * Parses DLMS profile values that are domain codes (not {@code event_code}).
 */
public final class HouseholdDomainCodeParser {

    private HouseholdDomainCodeParser() {
    }

    public static Integer parseCode(Object raw) {
        if (raw == null) {
            return null;
        }
        Integer asInt = EventRowValueParser.parseEventCode(raw);
        if (asInt != null) {
            return asInt;
        }
        String text = EventRowValueParser.parseStringValue(raw);
        if (text == null) {
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
