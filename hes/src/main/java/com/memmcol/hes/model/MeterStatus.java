package com.memmcol.hes.model;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MeterStatus {
    private String serial;
    private String status;
    private long timestamp;
}
