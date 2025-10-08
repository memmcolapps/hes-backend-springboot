package com.memmcol.hes.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeterDTO {
    private String meterNumber;
    private String meterModel;
    private String meterClass;
    private boolean MD;
    private LocalDateTime createdAt;

        // Optional: convenience method
    public void determineMD() {
        this.MD = "MD".equalsIgnoreCase(this.meterClass);
    }
}