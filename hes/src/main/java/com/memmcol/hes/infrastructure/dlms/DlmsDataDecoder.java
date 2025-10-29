package com.memmcol.hes.infrastructure.dlms;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class DlmsDataDecoder {
    // 🧠 Universal OctetString decoder
    public static Object decodeOctetString(byte[] data) {
        if (data == null || data.length == 0)
            return "";

        // --- Step 1️⃣: Try DLMS datetime ---
        if (data.length >= 12 && data.length <= 14) {
            try {
                LocalDateTime dt = decodeDlmsDateTime(data);
                if (dt != null)
                    return dt;
            } catch (Exception ignore) {
            }
        }

        // --- Step 2️⃣: Printable ASCII ---
        boolean allAsciiPrintable = true;
        for (byte b : data) {
            int val = b & 0xFF;
            if (val < 32 || val > 126) {
                allAsciiPrintable = false;
                break;
            }
        }

        if (allAsciiPrintable) {
            return new String(data, StandardCharsets.US_ASCII).trim();
        }

        // --- Step 3️⃣: BCD numeric check ---
        int validBcdCount = 0;
        for (byte b : data) {
            int high = (b >> 4) & 0xF;
            int low = b & 0xF;
            if (high <= 9 && low <= 9)
                validBcdCount++;
        }

        boolean looksLikeBcd = validBcdCount >= (data.length * 0.8);
        if (looksLikeBcd) {
            StringBuilder sb = new StringBuilder();
            for (byte b : data) {
                int high = (b >> 4) & 0xF;
                int low = b & 0xF;
                sb.append(high).append(low);
            }
            return sb.toString();
        }

        // --- Step 4️⃣: Fallback HEX ---
        StringBuilder hex = new StringBuilder();
        for (byte b : data)
            hex.append(String.format("%02X", b));
        return hex.toString();
    }

    // 🧩 Decode DLMS DateTime
    private static LocalDateTime decodeDlmsDateTime(byte[] bytes) {
        if (bytes.length < 12)
            return null;

        int year = ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
        int month = bytes[2] & 0xFF;
        int day = bytes[3] & 0xFF;
        int hour = bytes[4] & 0xFF;
        int minute = bytes[5] & 0xFF;
        int second = bytes[6] & 0xFF;

        if (month < 1 || month > 12 || day < 1 || day > 31)
            return null;

        try {
            return LocalDateTime.of(year, month, day, hour, minute, Math.min(second, 59)).truncatedTo(ChronoUnit.SECONDS);
        } catch (Exception e) {
            return null;
        }
    }
}
