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

    @Column(name = "model_number", nullable = false, length = 50)
    private String modelNumber;

    @Id
    @Column(name = "entry_timestamp", nullable = false)
    private LocalDateTime entryTimestamp;

    @Column(name = "meter_health_indicator")
    private Integer meterHealthIndicator;

    @Column(name = "instantaneous_voltage_l1")
    private Double instantaneousVoltageL1;

    @Column(name = "instantaneous_voltage_l2")
    private Double instantaneousVoltageL2;

    @Column(name = "instantaneous_voltage_l3")
    private Double instantaneousVoltageL3;

    @Column(name = "instantaneous_current_l1")
    private Double instantaneousCurrentL1;

    @Column(name = "instantaneous_current_l2")
    private Double instantaneousCurrentL2;

    @Column(name = "instantaneous_current_l3")
    private Double instantaneousCurrentL3;

    @Column(name = "instantaneous_active_power")
    private Double instantaneousActivePower;

    @Column(name = "instantaneous_reactive_import")
    private Double instantaneousReactiveImport;

    @Column(name = "instantaneous_reactive_export")
    private Double instantaneousReactiveExport;

    @Column(name = "instantaneous_power_factor")
    private Double instantaneousPowerFactor;

    @Column(name = "instantaneous_apparent_power")
    private Double instantaneousApparentPower;

    @Column(name = "instantaneous_net_frequency")
    private Double instantaneousNetFrequency;

    @Column(name = "received_at", insertable = false, updatable = false)
    private LocalDateTime receivedAt;
}
