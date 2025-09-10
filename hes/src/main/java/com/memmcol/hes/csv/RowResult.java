package com.memmcol.hes.csv;

import lombok.Getter;

@Getter
public class RowResult {
    private boolean success;
    private boolean skipped;
    private String message;

    public RowResult(boolean success, boolean skipped, String message) {
        this.success = success;
        this.skipped = skipped;
        this.message = message;
    }

}
