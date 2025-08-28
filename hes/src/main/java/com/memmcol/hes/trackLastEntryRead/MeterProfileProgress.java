package com.memmcol.hes.trackLastEntryRead;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

//Track Last Entry Read

@Entity
@Table(name = "meter_profile_progress", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"meter_serial", "profile_obis"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeterProfileProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meter_serial", nullable = false, length = 64)
    private String meterSerial;

    @Column(name = "profile_obis", nullable = false, length = 32)
    private String profileObis;

    @Column(name = "last_entry_index", nullable = false)
    private int lastEntryIndex;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

