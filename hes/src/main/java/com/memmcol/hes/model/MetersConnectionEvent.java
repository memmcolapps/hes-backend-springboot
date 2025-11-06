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

    @Column(name = "online_time")
    private LocalDateTime onlineTime;

    @Column(name = "offline_time")
    private LocalDateTime offlineTime;
}
