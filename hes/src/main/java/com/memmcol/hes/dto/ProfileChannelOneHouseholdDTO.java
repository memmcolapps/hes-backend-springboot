package com.memmcol.hes.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileChannelOneHouseholdDTO {
    private String meterSerial;
    private String modelNumber;
    private LocalDateTime entryTimestamp;

    private Double activeEnergyImport;
    private Double activeEnergyImportOnGrid;
    private Double activeEnergyImportOffGrid;

    private Double activeEnergyExport;
    private Double activeEnergyExportOnGrid;
    private Double activeEnergyExportOffGrid;

    private LocalDateTime receivedAt;
}
