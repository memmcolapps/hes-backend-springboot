package com.memmcol.hesTraining.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record ProfileRequest(
        String meterSerial,
        String meterModel,
        String profileObis,
        boolean isMD,

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime startDate,

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime endDate
) {
}
