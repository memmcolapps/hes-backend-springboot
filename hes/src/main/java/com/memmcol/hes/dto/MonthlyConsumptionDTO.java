package com.memmcol.hes.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyConsumptionDTO {
    private String meterSerial;
    private LocalDate monthStart;
    private Double prevValueKwh;
    private Double currValueKwh;
    private Double consumptionKwh;
}
