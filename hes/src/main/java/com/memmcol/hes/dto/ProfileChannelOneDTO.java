package com.memmcol.hes.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileChannelOneDTO {
    private LocalDateTime entryTimestamp;
    private String meterSerial;
    private String modelNumber;
    private Integer meterHealthIndicator;
    private Double totalInstantaneousActivePower;
    private Double totalInstantaneousApparentPower;
    private Double l1CurrentHarmonicThd;
    private Double l2CurrentHarmonicThd;
    private Double l3CurrentHarmonicThd;
    private Double l1VoltageHarmonicThd;
    private Double l2VoltageHarmonicThd;
    private Double l3VoltageHarmonicThd;
    private LocalDateTime receivedAt;
}