package com.memmcol.hes.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HouseholdRechargeTokenEventDTO {
    private String meterSerial;
    private String meterModel;
    private String profileObis;
    private Integer eventCode;
    private LocalDateTime eventTime;
    private Double rechargeAmountKwh;
    private String rechargeToken;
}
