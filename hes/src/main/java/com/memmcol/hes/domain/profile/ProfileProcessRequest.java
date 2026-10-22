package com.memmcol.hes.domain.profile;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProfileProcessRequest {
    private String model;
    private List<String> meterSerial;
    private String profileObis;
    private LocalDateTime from;
    private LocalDateTime to;
    private boolean mdMeter;
}
