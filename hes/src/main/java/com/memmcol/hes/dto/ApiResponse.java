package com.memmcol.hes.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ApiResponse<T> {

    private String status;
    private String message;
    private T data;
    private LocalDateTime timestamp;
}