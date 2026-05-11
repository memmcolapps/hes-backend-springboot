package com.memmcol.hes.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileChannelThreeHouseholdDTO {
    private String meterSerial;
    private String modelNumber;
    private LocalDateTime entryTimestamp;

    private Double activePowerL1;
    private Double activePowerL2;
    private Double activePowerL3;
    private Double powerFactorL1;
    private Double powerFactorL2;
    private Double powerFactorL3;
    private Double gridFrequency;

    private LocalDateTime receivedAt;
}
