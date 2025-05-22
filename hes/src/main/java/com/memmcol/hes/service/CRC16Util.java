package com.memmcol.hes.service;

public class CRC16Util {
    public static byte[] calculateCRC16(byte[] data) {
        int crc = 0xFFFF;
        for (byte b : data) {
            crc ^= (b & 0xFF);
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >> 1) ^ 0xA001;
                } else {
                    crc >>= 1;
                }
            }
        }
        return new byte[]{(byte) (crc & 0xFF), (byte) ((crc >> 8) & 0xFF)};
    }
}
