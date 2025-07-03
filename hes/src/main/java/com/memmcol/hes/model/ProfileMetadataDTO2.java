package com.memmcol.hes.model;

import java.time.Instant;
import java.util.List;

public class ProfileMetadataDTO2 {
    private String serial;
    private String obisCode;
    private int entriesInUse;
    private List<String> captureObisCodes; // e.g., ["1.0.0.2.0.255", "1.0.1.8.0.255"]
    private Instant lastUpdated;


}
