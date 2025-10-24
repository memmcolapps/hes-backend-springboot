package com.memmcol.hesTraining.dto;

import lombok.*;

@Data
@Builder
//@NoArgsConstructor
public class CaptureObjectsDTO {
    private String meterSerial;
    private String meterModel;
    private String profileObis;
    private String captureObis;
    private int classId;
    private int attributeIndex;
    private Double scaler;
    private String unit;
}
