package com.memmcol.hes.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HouseholdFraudEventDTO {
    private String meterSerial;
    private String meterModel;
    private String profileObis;
    private Integer eventCode;
    private LocalDateTime eventTime;
    private Double totalAbsoluteActiveKwh;
    private Double balanceKwh;
}
