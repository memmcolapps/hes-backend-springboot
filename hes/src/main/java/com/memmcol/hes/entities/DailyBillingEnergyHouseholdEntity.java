package com.memmcol.hes.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "daily_billing_energy_hh", schema = "public")
@IdClass(BillingHouseholdId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyBillingEnergyHouseholdEntity {
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

    @Column(name = "active_energy_import")
    private Double activeEnergyImport;

    @Column(name = "active_energy_import_ongrid")
    private Double activeEnergyImportOngrid;

    @Column(name = "active_energy_import_offgrid")
    private Double activeEnergyImportOffgrid;

    @Column(name = "active_energy_export_ongrid")
    private Double activeEnergyExportOngrid;

    @Column(name = "active_energy_export_offgrid")
    private Double activeEnergyExportOffgrid;

    @Column(name = "active_energy_export")
    private Double activeEnergyExport;
}

