package com.memmcol.hes.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class EventLogDTO {
    private String meterSerial;
    private Integer eventCode;
    private LocalDateTime eventTime;
    private String phase; // optional
    private String details;
}
