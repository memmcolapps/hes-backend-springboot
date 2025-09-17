package com.memmcol.hes.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "meters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Meter {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "meter_number", nullable = false, unique = true)
    private String meterNumber;

    @Column(name = "meter_model", nullable = false)
    private String meterModel;

    @Column(name = "meter_category", nullable = false)
    private String meterCategory;

    @Column(name = "meter_class", nullable = false)
    private String meterClass;

    @Column(name = "meter_type", nullable = false)
    private String meterType;

    @Column(name = "status", nullable = false)
    private Boolean status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}