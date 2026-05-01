package com.memmcol.hes.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingEnergyHouseholdDTO {
    private String meterSerial;
    private String meterModel;
    private LocalDateTime entryTimestamp;
    private LocalDateTime receivedAt;

    private Double activeEnergyImport;
    private Double activeEnergyImportOngrid;
    private Double activeEnergyImportOffgrid;
    private Double activeEnergyExportOngrid;
    private Double activeEnergyExportOffgrid;
    private Double activeEnergyExport;
}

