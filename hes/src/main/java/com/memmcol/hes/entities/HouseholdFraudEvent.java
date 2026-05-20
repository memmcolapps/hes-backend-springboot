package com.memmcol.hes.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "household_fraud_event",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_hh_fraud_event",
                columnNames = {"meter_serial", "event_code", "event_time"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HouseholdFraudEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meter_serial", nullable = false, length = 50)
    private String meterSerial;

    @Column(name = "meter_model", nullable = false, length = 50)
    private String meterModel;

    @Column(name = "profile_obis", nullable = false, length = 32)
    private String profileObis;

    @Column(name = "event_type_id", nullable = false)
    private Integer eventTypeId;

    @Column(name = "event_code", nullable = false)
    private Integer eventCode;

    @Column(name = "event_name", length = 255)
    private String eventName;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    @Column(name = "total_absolute_active_kwh")
    private Double totalAbsoluteActiveKwh;

    @Column(name = "balance_kwh")
    private Double balanceKwh;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
