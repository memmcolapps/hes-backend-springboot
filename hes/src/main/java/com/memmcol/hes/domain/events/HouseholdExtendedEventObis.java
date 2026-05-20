package com.memmcol.hes.domain.events;

/**
 * Household meter OBIS for fraud / control event profiles (same LN as MD tier, extended capture list).
 */
public final class HouseholdExtendedEventObis {

    public static final String FRAUD = "0.0.99.98.1.255";
    public static final String CONTROL = "0.0.99.98.2.255";

    private HouseholdExtendedEventObis() {
    }

    public static boolean isFraud(String profileObis) {
        return matches(profileObis, FRAUD);
    }

    public static boolean isControl(String profileObis) {
        return matches(profileObis, CONTROL);
    }

    public static boolean isHouseholdExtendedEvent(String profileObis) {
        return isFraud(profileObis) || isControl(profileObis);
    }

    private static boolean matches(String profileObis, String canonical) {
        if (profileObis == null || profileObis.isBlank()) {
            return false;
        }
        return canonical.equals(ObisCodeNormalizer.normalize(profileObis));
    }
}
