package com.memmcol.hes.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "event_type")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;   // e.g. "Power Grid Event Logs"

    @Column(name = "obis_code", nullable = false, length = 20)
    private String obisCode;   // e.g. "0.0.99.98.4.255"

    @Column(columnDefinition = "TEXT")
    private String description;
}
