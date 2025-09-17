package com.memmcol.hes.domain.profile;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProfileProcessRequest {
    private String model;
    private String meterSerial;
    private String profileObis;
    private LocalDateTime from;
    private LocalDateTime to;
    private boolean mdMeter;
}
