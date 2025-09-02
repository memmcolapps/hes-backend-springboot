package com.memmcol.hes.entities;

import com.memmcol.hes.dto.MonthlyBillingDTO;

public class MonthlyBillingToEntity {
    public static MonthlyBillingEntity toEntity(MonthlyBillingDTO dto) {
        return MonthlyBillingEntity.builder()
                .entryTimestamp(dto.getEntryTimestamp())
                .meterSerial(dto.getMeterSerial())
                .meterModel(dto.getMeterModel())
                .totalAbsoluteActiveEnergy(dto.getTotalAbsoluteActiveEnergy())
                .exportActiveEnergy(dto.getExportActiveEnergy())
                .importActiveEnergy(dto.getImportActiveEnergy())
                .importReactiveEnergy(dto.getImportReactiveEnergy())
                .exportReactiveEnergy(dto.getExportReactiveEnergy())
                .remainingCreditAmount(dto.getRemainingCreditAmount())
                .importActiveMd(dto.getImportActiveMd())
                .importActiveMdTime(dto.getImportActiveMdTime())
                .t1ActiveEnergy(dto.getT1ActiveEnergy())
                .t2ActiveEnergy(dto.getT2ActiveEnergy())
                .t3ActiveEnergy(dto.getT3ActiveEnergy())
                .t4ActiveEnergy(dto.getT4ActiveEnergy())
                .totalActiveEnergy(dto.getTotalActiveEnergy())
                .totalApparentEnergy(dto.getTotalApparentEnergy())
                .t1TotalApparentEnergy(dto.getT1TotalApparentEnergy())
                .t2TotalApparentEnergy(dto.getT2TotalApparentEnergy())
                .t3TotalApparentEnergy(dto.getT3TotalApparentEnergy())
                .t4TotalApparentEnergy(dto.getT4TotalApparentEnergy())
                .activeMaximumDemand(dto.getActiveMaximumDemand())
                .totalApparentDemand(dto.getTotalApparentDemand())
                .totalApparentDemandTime(dto.getTotalApparentDemandTime())
                .receivedAt(dto.getReceivedAt())
                .build();
    }
}
