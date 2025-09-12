package com.memmcol.hes.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeterDTO {

    private String meterNumber;
    private String meterModel;
    private String meterCategory;
    private String meterClass;
    private String meterType;
    private Boolean status;
    private LocalDateTime createdAt;
}