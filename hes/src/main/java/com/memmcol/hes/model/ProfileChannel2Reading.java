package com.memmcol.hes.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "profile_channel_2_readings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_profile2_unique",
                        columnNames = {"meter_serial", "entry_timestamp"})
        },
        indexes = {
                @Index(name = "idx_profile2_meter", columnList = "meter_serial"),
                @Index(name = "idx_profile2_timestamp", columnList = "entry_timestamp")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileChannel2Reading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meter_serial", nullable = false, length = 64)
    private String meterSerial;

    @Column(name = "model_number", nullable = false, length = 64)
    private String modelNumber;

    @Column(name = "entry_index", nullable = true)
    private int entryIndex;  // ← From meter (e.g. captureObject[0])

    @Column(name = "entry_timestamp", nullable = false)
    private LocalDateTime entryTimestamp;  // ← Usually in captureObject or computed

    @Column(name = "reactive_energy_kvarh")
    private Double reactiveEnergy;

    @Column(name = "active_energy_kwh")
    private Double activeEnergy;

    @Column(name = "raw_data", columnDefinition = "TEXT")
    private String rawData;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;
}
