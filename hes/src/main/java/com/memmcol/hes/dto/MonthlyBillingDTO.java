package com.memmcol.hes.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyBillingDTO {

    private String meterSerial;
    private String meterModel;
    private LocalDateTime entryTimestamp;
    private LocalDateTime receivedAt;

    private Double totalAbsoluteActiveEnergy;
    private Double exportActiveEnergy;
    private Double importActiveEnergy;
    private Double importReactiveEnergy;
    private Double exportReactiveEnergy;
    private Double remainingCreditAmount;
    private Double importActiveMd;
    private LocalDateTime importActiveMdTime;
    private Double t1ActiveEnergy;
    private Double t2ActiveEnergy;
    private Double t3ActiveEnergy;
    private Double t4ActiveEnergy;
    private Double totalActiveEnergy;
    private Double totalApparentEnergy;
    private Double t1TotalApparentEnergy;
    private Double t2TotalApparentEnergy;
    private Double t3TotalApparentEnergy;
    private Double t4TotalApparentEnergy;
    private Double activeMaximumDemand;
    private Double totalApparentDemand;
    private LocalDateTime totalApparentDemandTime;
}

