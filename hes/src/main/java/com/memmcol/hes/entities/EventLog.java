package com.memmcol.hes.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "event_log",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_event_log", columnNames = {"meter_serial", "event_code", "event_time"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meter_serial", nullable = false, length = 50)
    private String meterSerial;

    @Column(name = "event_type_id", nullable = false)
    private Long eventType;

    @Column(name = "event_code", nullable = false)
    private Integer eventCode;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    @Column(name = "current_threshold", length = 255)
    private Double currentThreshold;

    @Column(length = 255)
    private String eventName;   // <-- just the event name

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

//    @PrePersist
//    protected void onCreate() {
//        if (createdAt == null) {
//            createdAt = LocalDateTime.now();
//        }
//    }
}
