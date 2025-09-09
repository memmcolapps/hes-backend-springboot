package com.memmcol.hes.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "event_log",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"meter_serial", "event_code", "event_time"}
        ))
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

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "event_type_id", nullable = false)
    private EventType eventType;

    @Column(name = "event_code", nullable = false)
    private Integer eventCode;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    @Column(length = 10)
    private String phase;   // optional (L1, L2, L3, N)

    @Column(length = 255)
    private String details;   // <-- just the event description

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

//    @PrePersist
//    protected void onCreate() {
//        if (createdAt == null) {
//            createdAt = LocalDateTime.now();
//        }
//    }
}
