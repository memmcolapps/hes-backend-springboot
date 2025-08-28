package com.memmcol.hes.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TimestampRequest {
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Integer count; // Optional
}