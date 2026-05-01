package com.memmcol.hes.entities;

import com.memmcol.hes.dto.BillingDataHouseholdDTO;
import com.memmcol.hes.dto.BillingEnergyHouseholdDTO;

public class BillingHouseholdToEntity {
    public static DailyBillingDataHouseholdEntity toDailyData(BillingDataHouseholdDTO dto) {
        if (dto == null) return null;
        return DailyBillingDataHouseholdEntity.builder()
                .meterSerial(dto.getMeterSerial())
                .meterModel(dto.getMeterModel())
                .entryTimestamp(dto.getEntryTimestamp())
                .creditOngrid(dto.getCreditOngrid())
                .creditOffgrid(dto.getCreditOffgrid())
                .receivedAt(dto.getReceivedAt())
                .build();
    }

    public static MonthlyBillingDataHouseholdEntity toMonthlyData(BillingDataHouseholdDTO dto) {
        if (dto == null) return null;
        return MonthlyBillingDataHouseholdEntity.builder()
                .meterSerial(dto.getMeterSerial())
                .meterModel(dto.getMeterModel())
                .entryTimestamp(dto.getEntryTimestamp())
                .creditOngrid(dto.getCreditOngrid())
                .creditOffgrid(dto.getCreditOffgrid())
                .receivedAt(dto.getReceivedAt())
                .build();
    }

    public static DailyBillingEnergyHouseholdEntity toDailyEnergy(BillingEnergyHouseholdDTO dto) {
        if (dto == null) return null;
        return DailyBillingEnergyHouseholdEntity.builder()
                .meterSerial(dto.getMeterSerial())
                .meterModel(dto.getMeterModel())
                .entryTimestamp(dto.getEntryTimestamp())
                .activeEnergyImport(dto.getActiveEnergyImport())
                .activeEnergyImportOngrid(dto.getActiveEnergyImportOngrid())
                .activeEnergyImportOffgrid(dto.getActiveEnergyImportOffgrid())
                .activeEnergyExportOngrid(dto.getActiveEnergyExportOngrid())
                .activeEnergyExportOffgrid(dto.getActiveEnergyExportOffgrid())
                .activeEnergyExport(dto.getActiveEnergyExport())
                .receivedAt(dto.getReceivedAt())
                .build();
    }

    public static MonthlyBillingEnergyHouseholdEntity toMonthlyEnergy(BillingEnergyHouseholdDTO dto) {
        if (dto == null) return null;
        return MonthlyBillingEnergyHouseholdEntity.builder()
                .meterSerial(dto.getMeterSerial())
                .meterModel(dto.getMeterModel())
                .entryTimestamp(dto.getEntryTimestamp())
                .activeEnergyImport(dto.getActiveEnergyImport())
                .activeEnergyImportOngrid(dto.getActiveEnergyImportOngrid())
                .activeEnergyImportOffgrid(dto.getActiveEnergyImportOffgrid())
                .activeEnergyExportOngrid(dto.getActiveEnergyExportOngrid())
                .activeEnergyExportOffgrid(dto.getActiveEnergyExportOffgrid())
                .activeEnergyExport(dto.getActiveEnergyExport())
                .receivedAt(dto.getReceivedAt())
                .build();
    }
}

