package com.memmcol.hes.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeterUpdateDTO {
    private String meterNo;
    private String status;
    private LocalDateTime timestamp;
}
