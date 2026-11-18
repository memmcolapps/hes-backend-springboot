package com.memmcol.hes.entities;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "clock_sync_job")
@Getter
@Setter
public class ClockSyncJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String status = "PENDING";

    @Column(name = "total_meters", nullable = false)
    private long totalMeters;

    @Column(name = "last_registered_meter_id")
    private UUID lastRegisteredMeterId;

    @Column(name = "registration_completed", nullable = false)
    private boolean registrationCompleted;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_message")
    private String errorMessage;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

