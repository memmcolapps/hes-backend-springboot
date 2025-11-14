package com.memmcol.hes.gridflex.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ObisDto {
    private String obisString;
    private String groupName;
    private String description;
    private double scaler;
    private String unit;
}
