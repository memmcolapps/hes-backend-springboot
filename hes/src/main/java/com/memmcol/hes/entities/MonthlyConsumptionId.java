package com.memmcol.hes.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyConsumptionId implements Serializable {
    private String meterSerial;
    private LocalDate monthStart;
}
