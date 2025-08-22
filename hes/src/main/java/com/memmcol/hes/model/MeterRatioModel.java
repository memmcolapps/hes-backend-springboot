package com.memmcol.hes.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "meter_ratio_record",
        uniqueConstraints = {
                @UniqueConstraint(name = "uc_meter_serial", columnNames = "meter_serial")
        },
        indexes = {
                @Index(name = "idx_meter_serial", columnList = "meter_serial")
        }
)
@Getter
@Setter
public class MeterRatioModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meter_serial", nullable = false)
    private String meterSerial;

    @Column(name = "ct_ratio", nullable = false)
    private Integer ctRatio;

    @Column(name = "pt_ratio", nullable = false)
    private Integer ptRatio;

    @Column(name = "ctpt_ratio", nullable = false)
    private Integer ctptRatio;

    @Column(name = "read_time", nullable = false)
    private LocalDateTime readTime;
}
