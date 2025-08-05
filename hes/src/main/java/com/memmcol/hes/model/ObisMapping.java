package com.memmcol.hes.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "obis_mapping",
        indexes = {
                @Index(name = "ix_obis_code_combined", columnList = "obis_code_combined"),
                @Index(name = "ix_obis_code", columnList = "obis_code"),
                @Index(name = "ux_model_obis_code_combined", columnList = "model, obis_code_combined", unique = true)
        }
)
@Getter
@Setter
public class ObisMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "obis_code", nullable = false)
    private String obisCode;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "data_type")
    private String dataType;

    @Column(name = "class_id", nullable = false)
    private int classId;

    @Column(name = "attribute_index", nullable = false)
    private int attributeIndex;

    @Column(name="data_index", nullable = false)
    private int dataIndex;

    @Column(name="scaler", nullable = true)
    private Double scaler;

    @Column(name="unit", nullable = true)
    private String unit;

    @Column(name="group_name", nullable = true)
    private String groupName;

    @Column(name = "obis_code_combined", nullable = false)
    private String obisCodeCombined;

    @Column(name = "model", nullable = false)
    private String model;

    @Column(name = "purpose", nullable = true)
    private String purpose;  // 🔁 NEW FIELD
}
