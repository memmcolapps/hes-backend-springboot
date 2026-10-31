package com.memmcol.hes.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;



@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlmsResponse {
    private DlmsResponseStatus status;
    private String message;
    private String meterSerial;
    private Map<String, Object> resultData;
    private String rawResponse;

    public boolean isSuccess() {
        return DlmsResponseStatus.SUCCESS.equals(status);
    }
}

