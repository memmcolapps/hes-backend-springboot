package com.memmcol.hes.service;

public class StsCheckDigitCalculator {
    // Calculates the check digit using STS (mod 10 / Luhn algorithm)
    public static int calculateCheckDigit(String meterNumberWithoutCheckDigit) {
        int sum = 0;
        boolean doubleDigit = true; // Start from rightmost digit before check digit

        // Process digits from right to left
        for (int i = meterNumberWithoutCheckDigit.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(meterNumberWithoutCheckDigit.charAt(i));
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) digit -= 9;
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }

        int mod10 = sum % 10;
        return (10 - mod10) % 10; // Check digit
    }

    // Verify if a full meter number (including check digit) is valid
    public static boolean validateMeterNumber(String fullMeterNumber) {
        String prefix = fullMeterNumber.substring(0, fullMeterNumber.length() - 1);
        int expectedCheck = calculateCheckDigit(prefix);
        int actualCheck = Character.getNumericValue(fullMeterNumber.charAt(fullMeterNumber.length() - 1));
        return expectedCheck == actualCheck;
    }

    public static void main(String[] args) {
        String meter1 = "6231905227"; // without check digit (Actual STS meter number - 62319052270)
        String meter2 = "62320018757"; // full number with check digit

        int checkDigit1 = calculateCheckDigit(meter1);
        System.out.println("Meter 1 check digit = " + checkDigit1);
        System.out.println("Full Meter 1 = " + meter1 + checkDigit1);

        boolean isValid = validateMeterNumber(meter2);
        System.out.println("Meter 2 (" + meter2 + ") valid? " + isValid);
    }
}
