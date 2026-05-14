package com.memmcol.hes.exception;

// Thrown when an OBIS request string is malformed (bad delimiters, non-numeric
// parts, or an OBIS code that is not exactly six dot-separated bytes). Extends
// IllegalArgumentException so it maps to 400 via GlobalExceptionHandler if it
// ever escapes a tolerant batch loop.
public class InvalidObisFormatException extends IllegalArgumentException {
    private final String rawObis;

    public InvalidObisFormatException(String rawObis, String message) {
        super(message);
        this.rawObis = rawObis;
    }

    public String getRawObis() {
        return rawObis;
    }
}
