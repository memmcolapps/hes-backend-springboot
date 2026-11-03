package com.memmcol.hes.domain.profile;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString(includeFieldNames = true)    
public class ProfileSyncResult {
    private int totalIncoming;
    private int insertedCount;
    private int duplicateCount;
    private LocalDateTime previousLast;
    private LocalDateTime incomingMax;
    private LocalDateTime advanceTo;
    private boolean advanced;
}
