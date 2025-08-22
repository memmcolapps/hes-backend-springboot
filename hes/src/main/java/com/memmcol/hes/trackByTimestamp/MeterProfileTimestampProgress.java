package com.memmcol.hes.trackByTimestamp;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "meter_profile_timestamp_progress", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"meter_serial", "profile_obis"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeterProfileTimestampProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meter_serial", nullable = false)
    private String meterSerial;

    @Column(name = "profile_obis", nullable = false)
    private String profileObis;

    @Column(name = "last_profile_timestamp")
    private LocalDateTime lastProfileTimestamp;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}