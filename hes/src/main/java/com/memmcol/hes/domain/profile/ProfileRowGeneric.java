package com.memmcol.hes.domain.profile;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.Map;

@AllArgsConstructor
@ToString
@Getter
@Setter
public class ProfileRowGeneric {
    private Instant timestamp;
    private String meterSerial;
    private String profileObis;
    private Map<String, Object> values;
}