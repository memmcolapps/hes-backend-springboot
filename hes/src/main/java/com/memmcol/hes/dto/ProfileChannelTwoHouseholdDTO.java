package com.memmcol.hes.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileChannelTwoHouseholdDTO {
    private String meterSerial;
    private String modelNumber;
    private LocalDateTime entryTimestamp;

    private Double voltageL1;
    private Double voltageL2;
    private Double voltageL3;
    private Double currentL1;
    private Double currentL2;
    private Double currentL3;

    private LocalDateTime receivedAt;
}

