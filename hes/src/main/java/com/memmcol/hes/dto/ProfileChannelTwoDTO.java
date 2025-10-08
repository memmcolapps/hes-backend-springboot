package com.memmcol.hes.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileChannelTwoDTO {
    private String meterSerial;
    private String modelNumber;
    private LocalDateTime entryTimestamp;
    private Double totalImportActiveEnergy;
    private Double totalExportActiveEnergy;
    private LocalDateTime receivedAt;
}