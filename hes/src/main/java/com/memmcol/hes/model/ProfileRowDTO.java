package com.memmcol.hes.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Getter
@Setter
public class ProfileRowDTO {
    private int entryId;
    private Map<String, Object> values = new LinkedHashMap<>();
//    private byte[] rawData;  // ⬅️ Add this field to store raw DLMS data for that row (optional)
    private List<Object> rawData;
    private LocalDateTime profileTimestamp;
}
