package com.memmcol.hes.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingDataHouseholdDTO {
    private String meterSerial;
    private String meterModel;
    private LocalDateTime entryTimestamp;
    private LocalDateTime receivedAt;

    private Double creditOngrid;
    private Double creditOffgrid;
}

