package com.memmcol.hes.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "dlms_obis_objects")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DlmsObisObjectEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String obisCode;
    private int classId;
    private int version;
    private String type;
    private int attributeCount;
    private String accessRights;
    private String scaler;
    private String unit;
}
