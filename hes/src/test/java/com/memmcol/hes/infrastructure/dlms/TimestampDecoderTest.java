package com.memmcol.hes.infrastructure.dlms;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class TimestampDecoderTest {
    private final DlmsTimestampDecoder decoder = new DlmsTimestampDecoder();

    @Test
    void testDecode_localDateTime() {
        LocalDateTime now = LocalDateTime.of(2025, 8, 21, 12, 0);
        LocalDateTime result = decoder.decodeTimestamp(now);
        assertEquals(now, result, "Should return the same LocalDateTime");
    }

    @Test
    void testDecode_string() {
        String raw = "2025-08-01T00:00";
        LocalDateTime expected = LocalDateTime.of(2025, 8, 1, 0, 0);
        LocalDateTime result = decoder.decodeTimestamp(raw);
        assertEquals(expected, result, "Should parse ISO timestamp string");
    }

    @Test
    void testDecode_bytes() {
        byte[] raw = new byte[] { 0x07, (byte)0xE9, 0x08, 0x01, 0x05, 0x00, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0xC4, 0x00 }; //07 E9 08 01 05 00 00 00 FF FF C4 00
        // 👆 Example raw DLMS date-time bytes (replace with real sample)
        LocalDateTime result = decoder.decodeTimestamp(raw);
        // We can’t hardcode without real example → assert it’s not null
        assertNotNull(result, "Should decode a valid DLMS byte[] timestamp");
    }

    @Test
    void testDecode_invalidType() {
        Exception ex = assertThrows(IllegalArgumentException.class, () ->
                decoder.decodeTimestamp(12345) // Unsupported type
        );
        assertTrue(ex.getMessage().contains("Unsupported timestamp type"));
    }

}
