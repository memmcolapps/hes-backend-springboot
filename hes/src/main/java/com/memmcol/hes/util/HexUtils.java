package com.memmcol.hes.util;

public final class HexUtils {

    // Private constructor to prevent instantiation
    private HexUtils() {}

    // Convert byte array to hex string
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    public static String bytesToHex2(byte[][] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte[] innerBytes : bytes) {
            for (byte b : innerBytes) {
                sb.append(String.format("%02X", b)); // Format each byte
            }
            sb.append(" "); // Optional: Add a space between inner arrays for readability
        }
        return sb.toString().trim(); // Trim to remove the trailing space
    }
}