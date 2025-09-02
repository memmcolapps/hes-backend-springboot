package com.memmcol.hes.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyBillingProfileId implements Serializable {
    private String meterSerial;
    private LocalDateTime entryTimestamp;
}