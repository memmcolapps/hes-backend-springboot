package com.memmcol.hes.model;

import lombok.Data;

@Data
public class ObisObjectDTO {
    private String obisCode;
    private int classId;
    private int version;
    private String type;
    private int attributeCount;
    private String accessRights;
    private String scaler;
    private String unit;
}
