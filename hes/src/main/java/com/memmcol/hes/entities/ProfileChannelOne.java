package com.memmcol.hes.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "profile_channel_one",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_meter_entry", columnNames = {"meter_serial", "entry_timestamp"})
        }
)
@IdClass(ProfileChannelOneId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileChannelOne {

    @Id
    @Column(name = "meter_serial", nullable = false, length = 50)
    private String meterSerial;
    @Id
    @Column(name = "entry_timestamp", nullable = false)
    private LocalDateTime entryTimestamp;

    @Column(name = "model_number", nullable = false, length = 50)
    private String modelNumber;

    @Column(name = "meter_health_indicator")
    private Integer meterHealthIndicator;

    @Column(name = "total_instantaneous_active_power")
    private Double totalInstantaneousActivePower;

    @Column(name = "total_instantaneous_apparent_power")
    private Double totalInstantaneousApparentPower;

    @Column(name = "l1_current_harmonic_thd")
    private Double l1CurrentHarmonicThd;

    @Column(name = "l2_current_harmonic_thd")
    private Double l2CurrentHarmonicThd;

    @Column(name = "l3_current_harmonic_thd")
    private Double l3CurrentHarmonicThd;

    @Column(name = "l1_voltage_harmonic_thd")
    private Double l1VoltageHarmonicThd;

    @Column(name = "l2_voltage_harmonic_thd")
    private Double l2VoltageHarmonicThd;

    @Column(name = "l3_voltage_harmonic_thd")
    private Double l3VoltageHarmonicThd;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;
}

