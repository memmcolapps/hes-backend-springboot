package com.memmcol.hes.util;

import org.springframework.stereotype.Component;

@Component
public class DLMSRequestBuilder {

    public static byte[] buildLoadProfileRequest() {
        return new byte[] {
                0x00, 0x00, // Version = 0
                0x00, 0x11, // Source Address = 0x0011 (Client ID)
                0x00, 0x01, // Destination Address = 0x0001 (Server ID)
                0x00, 0x0C, // Length = 12 (payload size)
                (byte) 0xC0, // GET-REQUEST (Confirmed Service)
                0x01,       // Get-Request Normal
                0x00,       // Invoke ID
                0x03,       // Class ID = 3 (Register Object)
                0x01, 0x00, 0x01, 0x08, 0x00, (byte) 0xFF, // OBIS Code {1.0.1.8.0.255}
                0x02        // Attribute ID = 2 (Value)
        };
    }
}