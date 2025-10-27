package com.memmcol.hes.exception;

public class DlmsDataAccessException extends RuntimeException {
    public DlmsDataAccessException(String context, String message) {
        super(context + " → " + message);
    }
}
