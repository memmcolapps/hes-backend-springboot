package com.memmcol.hes.model;

import com.memmcol.hes.domain.profile.ObisObjectType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row = one captured column that belongs to a profile OBIS
 * for a particular meter model (firmware family).
 */
@Entity
@Table(
        name = "model_profile_metadata",
        indexes = {
                @Index(name = "idx_model_profile", columnList = "meter_model, profile_obis")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_model_profile_capture",
                        columnNames = {"meter_model", "profile_obis", "capture_obis", "attribute_index"}
                )
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelProfileMetadata {

    /** Primary-key surrogate for Hibernate (optional). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Example: “MMX-313-CT”. */
    @Column(name = "meter_model", nullable = false, length = 64)
    private String meterModel;

    /** Profile OBIS (e.g. 1.0.99.1.0.255). */
    @Column(name = "profile_obis", nullable = false, length = 32)
    private String profileObis;

    /** Captured object OBIS (e.g. 1.0.1.8.0.255). */
    @Column(name = "capture_obis", nullable = false, length = 32)
    private String captureObis;

    /** DLMS class id. */
    @Column(name = "class_id", nullable = false)
    private Integer classId;

    /** Attribute index (usually 2 or 3). */
    @Column(name = "attribute_index", nullable = false)
    private Integer attributeIndex;

    /** Scaler (defaults to 1.0). */
    @Column(name = "scaler", nullable = false)
    @Builder.Default
    private Double scaler = 1.0;

    /** Optional unit (kWh, V, …). */
    @Column(name = "unit")
    private String unit;

    /** Optional free-text description. */
    private String description;

    @Column(name = "capture_index", nullable = false)
    private Integer captureIndex = 0;

    @Column(name = "column_name", nullable = false)
    private String columnName = "";

    @Column(name = "multiply_by")
    private String multiplyBy = "CTPT";

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private ObisObjectType type;  // New field
}

