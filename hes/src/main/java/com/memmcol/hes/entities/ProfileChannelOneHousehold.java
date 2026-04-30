package com.memmcol.hes.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "profile_channel_one_hh", schema = "public")
@IdClass(ProfileChannelOneId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileChannelOneHousehold {
    @Id
    @Column(name = "meter_serial", nullable = false, length = 50)
    private String meterSerial;

    @Column(name = "model_number", nullable = false, length = 50)
    private String modelNumber;

    @Id
    @Column(name = "entry_timestamp", nullable = false)
    private LocalDateTime entryTimestamp;

    @Column(name = "active_energy_import")
    private Double activeEnergyImport;

    @Column(name = "active_energy_import_ongrid")
    private Double activeEnergyImportOnGrid;

    @Column(name = "active_energy_import_offgrid")
    private Double activeEnergyImportOffGrid;

    @Column(name = "active_energy_export")
    private Double activeEnergyExport;

    @Column(name = "active_energy_export_ongrid")
    private Double activeEnergyExportOnGrid;

    @Column(name = "active_energy_export_offgrid")
    private Double activeEnergyExportOffGrid;

    @Column(name = "received_at", insertable = false, updatable = false)
    private LocalDateTime receivedAt;
}
