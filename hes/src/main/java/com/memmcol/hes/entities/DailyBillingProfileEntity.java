package com.memmcol.hes.entities;

import com.memmcol.hes.dto.DailyBillingProfileDTO;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "daily_billing_profile")
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(DailyBillingProfileId.class) // Composite PK
public class DailyBillingProfileEntity {

    @Id
    @Column(name = "meter_serial", nullable = false, length = 50)
    private String meterSerial;

    @Id
    @Column(name = "entry_timestamp", nullable = false)
    private LocalDateTime entryTimestamp;

    @Column(name = "meter_model", nullable = false, length = 50)
    private String meterModel;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "total_active_energy")
    private Double totalActiveEnergy;

    @Column(name = "t1_active_energy")
    private Double t1ActiveEnergy;

    @Column(name = "t2_active_energy")
    private Double t2ActiveEnergy;

    @Column(name = "t3_active_energy")
    private Double t3ActiveEnergy;

    @Column(name = "t4_active_energy")
    private Double t4ActiveEnergy;

    @Column(name = "total_apparent_energy")
    private Double totalApparentEnergy;

    @Column(name = "t1_total_apparent_energy")
    private Double t1TotalApparentEnergy;

    @Column(name = "t3_total_apparent_energy")
    private Double t3TotalApparentEnergy;

    @Column(name = "t4_total_apparent_energy")
    private Double t4TotalApparentEnergy;

    public static DailyBillingProfileEntity toEntity(DailyBillingProfileDTO dto) {
        return DailyBillingProfileEntity.builder()
                .entryTimestamp(dto.getEntryTimestamp())
                .meterSerial(dto.getMeterSerial())
                .meterModel(dto.getMeterModel())
                .totalActiveEnergy(dto.getTotalActiveEnergy())
                .t1ActiveEnergy(dto.getT1ActiveEnergy())
                .t2ActiveEnergy(dto.getT2ActiveEnergy())
                .t3ActiveEnergy(dto.getT3ActiveEnergy())
                .t4ActiveEnergy(dto.getT4ActiveEnergy())
                .totalApparentEnergy(dto.getTotalApparentEnergy())
                .t1TotalApparentEnergy(dto.getT1TotalApparentEnergy())
                .t3TotalApparentEnergy(dto.getT3TotalApparentEnergy())
                .t4TotalApparentEnergy(dto.getT4TotalApparentEnergy())
                .receivedAt(dto.getReceivedAt())
                .build();
    }
}