package com.memmcol.hes.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "daily_billing_data_hh", schema = "public")
@IdClass(BillingHouseholdId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyBillingDataHouseholdEntity {
    @Id
    @Column(name = "meter_serial", nullable = false, length = 50)
    private String meterSerial;

    @Column(name = "meter_model", nullable = false, length = 50)
    private String meterModel;

    @Id
    @Column(name = "entry_timestamp", nullable = false, columnDefinition = "timestamp(0)")
    private LocalDateTime entryTimestamp;

    @Column(name = "received_at", insertable = false, updatable = false)
    private LocalDateTime receivedAt;

    @Column(name = "credit_ongrid")
    private Double creditOngrid;

    @Column(name = "credit_offgrid")
    private Double creditOffgrid;
}

