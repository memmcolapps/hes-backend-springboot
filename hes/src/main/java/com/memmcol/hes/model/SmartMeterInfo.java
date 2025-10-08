package com.memmcol.hes.model;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "smart_meter_info")
public class SmartMeterInfo {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "meter_model", nullable = false)
    private String meterModel;

    @Column(name = "protocol", nullable = false)
    private String protocol;

    @Column(name = "authentication", nullable = false)
    private String authentication;

    @Column(name = "password", nullable = false)
    private String password;

    // ✅ Relationship back to Meter
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meter_id", nullable = false)
    private MetersEntity meter;
}