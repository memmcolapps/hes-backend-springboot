package com.memmcol.hes.domain.events;

/**
 * Household meter OBIS for token event profiles (DLMS 0-0:99.98.x.255).
 */
public final class HouseholdTokenEventObis {

    public static final String RECHARGE = "0.0.99.98.3.255";
    public static final String MANAGEMENT = "0.0.99.98.5.255";

    private HouseholdTokenEventObis() {
    }

    public static boolean isRecharge(String profileObis) {
        return matches(profileObis, RECHARGE);
    }

    public static boolean isManagement(String profileObis) {
        return matches(profileObis, MANAGEMENT);
    }

    public static boolean isHouseholdTokenEvent(String profileObis) {
        return isRecharge(profileObis) || isManagement(profileObis);
    }

    private static boolean matches(String profileObis, String canonical) {
        if (profileObis == null || profileObis.isBlank()) {
            return false;
        }
        String normalized = profileObis.trim().replace('-', '.');
        return canonical.equals(normalized);
    }
}
