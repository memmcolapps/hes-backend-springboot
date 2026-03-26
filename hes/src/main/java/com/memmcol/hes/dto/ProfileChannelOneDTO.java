package com.memmcol.hes.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileChannelOneDTO {

    private String meterSerial;
    private String modelNumber;
    private LocalDateTime entryTimestamp;
    private Integer meterHealthIndicator;
    private Double instantaneousVoltageL1;
    private Double instantaneousVoltageL2;
    private Double instantaneousVoltageL3;
    private Double instantaneousCurrentL1;
    private Double instantaneousCurrentL2;
    private Double instantaneousCurrentL3;
    private Double instantaneousActivePower;
    private Double instantaneousReactiveImport;
    private Double instantaneousReactiveExport;
    private Double instantaneousPowerFactor;
    private Double instantaneousApparentPower;
    private Double instantaneousNetFrequency;
    private LocalDateTime receivedAt;
}
