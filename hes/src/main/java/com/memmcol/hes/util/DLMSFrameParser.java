package com.memmcol.hes.util;


import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class DLMSFrameParser {

    public static class LoginFrameData {
        private final String meterNumber;
        private final int status;
        private final boolean crcValid;
        private final String crc;

        public LoginFrameData(String meterNumber, int status, boolean crcValid, String crc) {
            this.meterNumber = meterNumber;
            this.status = status;
            this.crcValid = crcValid;
            this.crc = crc;
        }

        public String getMeterNumber() {
            return meterNumber;
        }

        public int getStatus() {
            return status;
        }

        public boolean isCrcValid() {
            return crcValid;
        }

        public String getCrc() {
            return crc;
        }
    }

    public static LoginFrameData parseLoginFrame(byte[] frame, boolean validateCRC) {
        if (frame[7] != 0x13) {
            throw new IllegalArgumentException("Invalid login frame command: " + String.format("0x%02X", frame[7]));
        }

        int serialLength = frame[10] & 0xFF;
        int expectedLength = 11 + serialLength;
        if (frame.length < expectedLength) {
            throw new IllegalArgumentException("Frame too short for declared serial length");
        }

        String meterNumber = new String(frame, 11, serialLength, StandardCharsets.US_ASCII);
        int status = (frame[11 + serialLength] & 0xFF) << 8 | (frame[12 + serialLength] & 0xFF);

        boolean crcValid = true;
        String crc = "";
        if (frame.length >= expectedLength + 2 && validateCRC) {
            byte[] dataToCheck = Arrays.copyOf(frame, expectedLength);
            short receivedCrc = (short) (((frame[expectedLength + 1] & 0xFF) << 8) | (frame[expectedLength] & 0xFF));
            short calculatedCrc = calculateCRC(dataToCheck, dataToCheck.length);
            crcValid = receivedCrc == calculatedCrc;
            crc = String.format("%02X%02X", frame[expectedLength], frame[expectedLength + 1]);
        }

        return new LoginFrameData(meterNumber, status, crcValid, crc);
    }

   private static short calculateCRC(byte[] data, int length) {
        int crc = 0xFFFF;
        for (int i = 0; i < length; i++) {
            crc ^= (data[i] & 0xFF);
            for (int j = 0; j < 8; j++) {
                crc = (crc & 0x0001) != 0 ? (crc >>> 1) ^ 0xA001 : (crc >>> 1);
            }
        }
        return (short) crc;
    }
}