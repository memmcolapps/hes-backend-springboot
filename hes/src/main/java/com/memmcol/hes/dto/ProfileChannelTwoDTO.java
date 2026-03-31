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
    private Integer meterHealthIndicator;
    private Double activeEnergyImport;
    private Double activeEnergyImportRate1;
    private Double activeEnergyImportRate2;
    private Double activeEnergyImportRate3;
    private Double activeEnergyImportRate4;
    private Double activeEnergyCombinedTotal;
    private Double activeEnergyExport;
    private Double reactiveEnergyImport;
    private Double reactiveEnergyExport;
    private Double apparentEnergyImport;
    private Double apparentEnergyExport;
    private LocalDateTime receivedAt;
}