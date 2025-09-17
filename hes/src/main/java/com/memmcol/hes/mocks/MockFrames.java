package com.memmcol.hes.mocks;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

public class MockFrames {
    private final List<String> rxFrames;

    public MockFrames(List<String> frames) {
        this.rxFrames = new ArrayList<>(frames);
    }

    public List<String> getFrames() {
        return new ArrayList<>(rxFrames);
    }

    public byte[] hexToBytes(String hex) {
        hex = hex.replaceAll("\\s+", "");
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
