package com.memmcol.hes.domain.profile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProfileSyncResult {
    private int totalIncoming;
    private int insertedCount;
    private int duplicateCount;
    private LocalDateTime previousLast;
    private LocalDateTime incomingMax;
    private LocalDateTime advanceTo;
    private boolean advanced;
}