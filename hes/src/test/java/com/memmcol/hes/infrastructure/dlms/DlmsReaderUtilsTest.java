package com.memmcol.hes.infrastructure.dlms;

import com.memmcol.hes.application.port.out.MeterLockPort;
import com.memmcol.hes.domain.profile.ObisObjectType;
import com.memmcol.hes.domain.profile.ProfileRowGeneric;
import com.memmcol.hes.model.ModelProfileMetadata;
import com.memmcol.hes.nettyUtils.SessionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DlmsReaderUtilsTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private MeterLockPort meterLockPort;

    @Mock
    private DlmsPartialDecoder partialDecoder;

    @Mock
    private DlmsTimestampDecoder timestampDecoder;

    @InjectMocks
    private DlmsReaderUtils utils;  // ✅ constructor injected with mocks

    /*TODO
    *  1. Try to get the frames for partial row after timeout.
    *  2. Try reading profile channel one or two
    *  2. Simulate here.*/

    @Test
    void testMapRawLists_simpleCase() {
        // Arrange
        List<ModelProfileMetadata> metadataList = List.of(
                new ModelProfileMetadata(null, "MMX-313-CT", "0.0.98.1.0.255",
                        "0.0.1.0.0.255", 8, 2, 1.0, "ts", "Entry time",
                        0, "entry_timestamp", "CTPT", ObisObjectType.CLOCK),
                new ModelProfileMetadata(null, "MMX-313-CT", "0.0.98.1.0.255",
                        "1.0.1.8.0.255", 1, 2, 1.0, "kWh", "Active energy",
                        1, "import_active_energy", "CTPT", ObisObjectType.SCALER)
        );

        // Fake raw row: [timestamp, energyValue]
        LocalDateTime ts = LocalDateTime.of(2025, 1, 1, 0, 0);
        List<Object> row = List.of(ts, 123.45);
        List<List<Object>> raw = List.of(row);

        // Mock timestamp decoder behavior
        when(timestampDecoder.toLocalDateTime(ts)).thenReturn(ts);

        // Act
        List<ProfileRowGeneric> result =
                utils.mapRawLists(raw, metadataList, "202006001314", "0.0.98.1.0.255", true);

        // Assert
        assertEquals(1, result.size(), "Should have exactly 1 mapped row");

        ProfileRowGeneric rowGeneric = result.get(0);

        // Timestamp check
        Instant expectedInstant = ts.atZone(ZoneId.systemDefault()).toInstant();
        assertEquals(expectedInstant, rowGeneric.getTimestamp(), "Timestamps should match");

        // Values check
        Map<String, Object> values = rowGeneric.getValues();
        assertEquals(ts, values.get("0.0.1.0.0.255"), "Should map timestamp OBIS correctly");
        assertEquals(123.45, values.get("1.0.1.8.0.255-2"), "Should map energy OBIS correctly");
    }
}
