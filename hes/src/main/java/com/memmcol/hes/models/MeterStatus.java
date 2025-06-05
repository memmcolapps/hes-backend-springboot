package com.memmcol.hes.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class MeterStatus {
    private String serial;
    private String status;
    private long timestamp;

}
