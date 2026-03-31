package com.memmcol.hes.entities;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileChannelOneV1 {

    private String meterSerial;
    private LocalDateTime entryTimestamp;

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

