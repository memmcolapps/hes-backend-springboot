package com.memmcol.hes.gridflex.sse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MeterStatusEvent {
    private String meterNo;
    private LocalDateTime lastSeen;
    private String status;   // ONLINE / OFFLINE
}