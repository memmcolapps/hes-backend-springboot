package com.memmcol.hes.gridflex.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RealtimeReadRequest {
    private String meterType; // e.g., "MD" or "NonMD"
    private List<String> meters; // List of meter serial numbers
    private List<String> obisString; // List of OBIS codes
}
