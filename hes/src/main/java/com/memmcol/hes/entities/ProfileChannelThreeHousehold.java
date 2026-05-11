package com.memmcol.hes.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "profile_channel_three_hh", schema = "public")
@IdClass(ProfileChannelThreeHouseholdId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileChannelThreeHousehold {
    @Id
    @Column(name = "meter_serial", nullable = false, length = 50)
    private String meterSerial;

    @Column(name = "model_number", nullable = false, length = 50)
    private String modelNumber;

    @Id
    @Column(name = "entry_timestamp", nullable = false, columnDefinition = "timestamp(0)")
    private LocalDateTime entryTimestamp;

    @Column(name = "active_power_l1")
    private Double activePowerL1;

    @Column(name = "active_power_l2")
    private Double activePowerL2;

    @Column(name = "active_power_l3")
    private Double activePowerL3;

    @Column(name = "power_factor_l1")
    private Double powerFactorL1;

    @Column(name = "power_factor_l2")
    private Double powerFactorL2;

    @Column(name = "power_factor_l3")
    private Double powerFactorL3;

    @Column(name = "grid_frequency")
    private Double gridFrequency;

    @Column(name = "received_at", insertable = false, updatable = false)
    private LocalDateTime receivedAt;
}
