package com.memmcol.hes.trackByTimestamp;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "meter_profile_state",
        uniqueConstraints = @UniqueConstraint(columnNames = {"meter_serial", "profile_obis"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeterProfileState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // AUTO for PostgreSQL/MySQL
    private Long id;

    @Column(name = "meter_serial", nullable = false, length = 64)
    private String meterSerial;

    @Column(name = "profile_obis", nullable = false, length = 32)
    private String profileObis;

    @Column(name = "last_timestamp")
    private LocalDateTime lastTimestamp; // Maps to SQL TIMESTAMP

    @Column(name = "capture_period_sec")
    private Integer capturePeriodSec;

    @Column(name = "updated_at", nullable = false,
            columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;
}


