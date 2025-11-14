package com.memmcol.hes.gridflex.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeterDto {
    private String meterSerial;
    private String meterModel;
    private boolean isMD;
}
