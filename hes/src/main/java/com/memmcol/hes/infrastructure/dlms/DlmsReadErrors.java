package com.memmcol.hes.infrastructure.dlms;

/**
 * DLMS read error classification helpers.
 */
public final class DlmsReadErrors {

    private DlmsReadErrors() {
    }

    /**
     * Gurux throws when {@code model_profile_metadata} capture-object count does not match
     * the profile buffer row width on the meter.
     */
    public static boolean isCaptureColumnMismatch(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("columns do not match")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
