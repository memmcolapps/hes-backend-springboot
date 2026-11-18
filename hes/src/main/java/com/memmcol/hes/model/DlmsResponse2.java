package com.memmcol.hes.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlmsResponse2 {

    private DlmsResponseStatus status;
    private String message;
    private String meterSerial;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long durationMs;

    @Builder.Default
    private List<String> requestFrames = new ArrayList<>();

    @Builder.Default
    private List<String> responseFrames = new ArrayList<>();

    public boolean isSuccess() {
        return status == DlmsResponseStatus.SUCCESS;
    }
}
