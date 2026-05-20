package com.memmcol.hes.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HouseholdControlEventDTO {
    private String meterSerial;
    private String meterModel;
    private String profileObis;
    private Integer eventCode;
    private LocalDateTime eventTime;
    /** Raw DLMS value before domain lookup (reason-of-operation code). */
    private Object reasonOfOperationRaw;
}
