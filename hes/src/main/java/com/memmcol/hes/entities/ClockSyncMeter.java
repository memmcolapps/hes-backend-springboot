package com.memmcol.hes.entities;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "clock_sync_meter",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_clock_sync_job_meter",
                columnNames = {"job_id", "meter_number"}
        )
)
@Getter
@Setter
public class ClockSyncMeter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "meter_number", nullable = false)
    private String meterNumber;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "target_date_time")
    private LocalDateTime targetDateTime;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "attempted_at")
    private LocalDateTime attemptedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "write_started_at")
    private LocalDateTime writeStartedAt;

    @Column(name = "write_completed_at")
    private LocalDateTime writeCompletedAt;

    @Column(name = "dlms_status", length = 50)
    private String dlmsStatus;

    @Column(name = "dlms_message", columnDefinition = "TEXT")
    private String dlmsMessage;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "request_frames", columnDefinition = "TEXT")
    private String requestFrames;

    @Column(name = "response_details", columnDefinition = "TEXT")
    private String responseDetails;

    @Column(name = "verification_status", length = 20)
    private String verificationStatus = "NOT_CHECKED";

    @Column(name = "observed_date_time")
    private LocalDateTime observedDateTime;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }
}

