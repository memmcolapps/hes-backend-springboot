package com.memmcol.hes.exception;

import lombok.Getter;

@Getter
public class DlmsTransportException extends RuntimeException {

    private final String code;

    public DlmsTransportException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public DlmsTransportException(String code, Throwable cause) {
        super(cause);
        this.code = code;
    }

    public DlmsTransportException(String code, String message) {
        super(message);
        this.code = code;
    }

}