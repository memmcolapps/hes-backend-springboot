package com.memmcol.hes.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
public class MetersConnectionEvent{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meter_No", unique = true, nullable = false, length = 12)
    private String meterNo;

    @Column(name = "eventType")
    private String eventType;

    @Column(name = "connection_time")
    private LocalDateTime connectionTime;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public MetersConnectionEvent() {
        this.connectionTime = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public MetersConnectionEvent(String meterNo, String eventType){
        this();
        this.meterNo = meterNo;
        this.eventType = eventType;
    }

}
