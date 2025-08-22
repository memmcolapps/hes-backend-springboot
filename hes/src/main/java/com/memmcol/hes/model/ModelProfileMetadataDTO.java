package com.memmcol.hes.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing captured object metadata for a specific meter model and profile OBIS.
 * Each record describes one column of a profile buffer including scaler, OBIS, and attribute details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelProfileMetadataDTO {

    /**
     * Meter model identifier (e.g., MMX-313-CT).
     */
    private String meterModel;

    /**
     * Profile OBIS code (e.g., 1.0.99.1.0.255).
     */
    private String profileObis;

    /**
     * OBIS code of the captured object (e.g., 1.0.1.8.0.255).
     */
    private String captureObis;

    /**
     * DLMS class ID of the captured object.
     */
    private int classId;

    /**
     * Attribute index of the captured object (usually 2 or 3).
     */
    private int attributeIndex;

    /**
     * Scaler to apply for numeric values (default is 1.0 if not defined).
     */
    private double scaler;

    /**
     * Unit of measurement (optional, e.g., kWh, V).
     */
    private String unit;

    /**
     * Optional column label or description.
     */
    private String description;
}