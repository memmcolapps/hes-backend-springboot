package com.memmcol.hes.service;

public class ObisDescriptionMapper{
    public static String describe(String obisCode, String unit) {
        switch (obisCode) {
            case "1.0.1.8.0.255":
                return "Active Energy (" + unit + ")";
            case "1.0.2.8.0.255":
                return "Reactive Energy (" + unit + ")";
            case "1.0.32.7.0.255":
                return "Voltage (" + unit + ")";
            // Add more known mappings...
            default:
                return "OBIS " + obisCode;
        }
    }
}

