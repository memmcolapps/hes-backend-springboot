package com.memmcol.hes.domain.profile.mappers;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EventRowValueParserTest {

    @Test
    void parseStringValue_decodesAsciiOctetString() {
        byte[] token = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        assertEquals("12345678901234567890", EventRowValueParser.parseStringValue(token));
    }

    @Test
    void parseStringValue_decodesBcdOctetString() {
        // two digits per byte: 12 34 56 → "123456"
        byte[] bcd = new byte[] {0x12, 0x34, 0x56};
        assertEquals("123456", EventRowValueParser.parseStringValue(bcd));
    }

    @Test
    void parseStringValue_doesNotUseByteArrayHashCode() {
        byte[] token = "9999".getBytes(StandardCharsets.US_ASCII);
        String parsed = EventRowValueParser.parseStringValue(token);
        assertNotEquals(token.toString(), parsed);
        assertEquals("9999", parsed);
    }

    @Test
    void parseStringValue_nullForEmptyString() {
        assertNull(EventRowValueParser.parseStringValue("   "));
    }
}
