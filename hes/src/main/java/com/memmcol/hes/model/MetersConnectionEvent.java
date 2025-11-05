package com.memmcol.hes.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "meters_connection_event")
public class MetersConnectionEvent {

    @Id
    @Column(name = "meter_no", nullable = false, length = 12)
    private String meterNo;

    @Column(name = "connection_type")
    private String connectionType;

    @Column(name = "connection_time")
    private LocalDateTime connectionTime;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public MetersConnectionEvent() {
        this.connectionTime = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}
