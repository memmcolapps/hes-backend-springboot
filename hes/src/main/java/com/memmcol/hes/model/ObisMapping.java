package com.memmcol.hes.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "obis_mapping",
        indexes = {
                @Index(name = "IX_OBISCode", columnList = "obis_code", unique = true)
        }
)
@Getter
@Setter
public class ObisMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "obis_code", unique = true, nullable = false)
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
    private int scaler;

    @Column(name="unit", nullable = true)
    private String unit;

    @Column(name="group_name", nullable = true)
    private String groupName;

    @Column(name="obis_code_combined", nullable = true)
    private String obisCodeCombined;
}
