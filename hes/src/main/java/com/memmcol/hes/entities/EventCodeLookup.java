package com.memmcol.hes.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "event_code_lookup")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventCodeLookup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "event_type_id", nullable = false)
    private EventType eventType;

    @Column(nullable = false)
    private Integer code;   // e.g. 101

    @Column(nullable = false, length = 255)
    private String description;   // e.g. "Power Failure"
}
