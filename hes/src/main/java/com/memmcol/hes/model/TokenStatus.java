package com.memmcol.hes.model;

public enum TokenStatus {
    SUCCESS(0x00),
    REJECT_TOKEN(0x01),
    USED_TOKEN(0x02),
    EXPIRED_TOKEN(0x03),
    UNKNOWN(-1);

    private final int code;

    TokenStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static TokenStatus fromCode(int code) {
        for (TokenStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return UNKNOWN;
    }

    public boolean isSuccess() {
        return this == SUCCESS;
    }
}