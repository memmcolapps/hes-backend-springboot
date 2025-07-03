package com.memmcol.hes.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Getter
@Setter
public class ProfileRowDTO {
    private int entryId;
    private Map<String, Object> values = new LinkedHashMap<>();
}
