package com.memmcol.hes.model;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ProfileChannel2ReadingDTO {
    private String meterSerial;
    private String modelNumber;

    private int entryIndex;
    private LocalDateTime profileTimestamp;

    private Double reactiveEnergyKvarh; // OBIS 1.0.2.8.0.255
    private Double activeEnergyKwh;     // OBIS 1.0.1.8.0.255

    private String rawData; // optional – for debugging

    private LocalDateTime receivedAt;
}
