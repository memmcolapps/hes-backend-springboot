package com.memmcol.hes.application.port.out;

/**
 * 2. Port Interfaces (Application Layer Contracts)
 * Exception
 */
public class ProfileReadException extends Exception {
    public ProfileReadException(String message) {
        super(message);
    }

    public ProfileReadException(String message, Throwable cause) {
        super(message, cause);
    }
}