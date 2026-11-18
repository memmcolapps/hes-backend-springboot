package com.memmcol.hes.dto;


import java.util.List;

public record ClockSyncRequest(
        List<String> meterNumbers
) {
}

