package com.memmcol.hes.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "meters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetersEntity {
    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "account_number", unique = true)
    private String accountNumber;

    @Column(name = "node_id")
    private UUID nodeId;

    @Column(name = "sim_number", nullable = false)
    private String simNumber;

    @Column(name = "meter_category", nullable = false)
    private String meterCategory;

    @Column(name = "meter_class", nullable = false)
    private String meterClass; // ⚡ used for isMD flag

    @Column(name = "meter_type", nullable = false)
    private String meterType;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "meter_number", unique = true, nullable = false)
    private String meterNumber;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "cin", unique = true)
    private String cin;

    @Column(name = "old_sgc", nullable = false)
    private String oldSgc = "0";

    @Column(name = "new_sgc", nullable = false)
    private String newSgc = "0";

    @Column(name = "new_krn", nullable = false)
    private String newKrn = "0";

    @Column(name = "old_krn", nullable = false)
    private String oldKrn = "0";

    @Column(name = "fixed_energy")
    private String fixedEnergy;

    @Column(name = "old_tariff_index", nullable = false)
    private Long oldTariffIndex = 1L;

    @Column(name = "new_tariff_index", nullable = false)
    private Long newTariffIndex = 1L;

    @Column(name = "dss")
    private UUID dss;

    @Column(name = "meter_manufacturer", nullable = false)
    private UUID meterManufacturer;

    @Column(name = "tariff")
    private UUID tariff;

    @Column(name = "smart_status")
    private boolean smartStatus = false;

    @Column(name = "image")
    private String image;

    @Column(name = "meter_stage")
    private String meterStage;

    // ✅ Relationship to SmartMeterInfo
    @OneToOne(mappedBy = "meter", cascade = CascadeType.ALL, optional = false)
    private SmartMeterInfo smartMeterInfo;
}