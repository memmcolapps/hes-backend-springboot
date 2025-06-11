package com.memmcol.hes.models;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MeterStatus {
    private String serial;
    private String status;
    private long timestamp;
}
