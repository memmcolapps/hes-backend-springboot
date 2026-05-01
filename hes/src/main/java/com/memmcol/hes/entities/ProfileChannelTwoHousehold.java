package com.memmcol.hes.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "profile_channel_two_hh", schema = "public")
@IdClass(ProfileChannelTwoHouseholdId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileChannelTwoHousehold {
    @Id
    @Column(name = "meter_serial", nullable = false, length = 50)
    private String meterSerial;

    @Column(name = "model_number", nullable = false, length = 50)
    private String modelNumber;

    @Id
    @Column(name = "entry_timestamp", nullable = false, columnDefinition = "timestamp(0)")
    private LocalDateTime entryTimestamp;

    @Column(name = "voltage_l1")
    private Double voltageL1;

    @Column(name = "voltage_l2")
    private Double voltageL2;

    @Column(name = "voltage_l3")
    private Double voltageL3;

    @Column(name = "current_l1")
    private Double currentL1;

    @Column(name = "current_l2")
    private Double currentL2;

    @Column(name = "current_l3")
    private Double currentL3;

    @Column(name = "received_at", insertable = false, updatable = false)
    private LocalDateTime receivedAt;
}

