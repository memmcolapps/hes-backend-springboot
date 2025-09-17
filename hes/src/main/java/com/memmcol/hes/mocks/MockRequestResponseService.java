package com.memmcol.hes.mocks;

import com.memmcol.hes.application.port.out.TxRxService;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.List;

@Slf4j
public class MockRequestResponseService implements TxRxService {
    private final MockFrames frames;
    private int index = 0;

    public MockRequestResponseService(List<String> frameList) {
        this.frames = new MockFrames(frameList);
    }

    @Override
    public byte[] sendReceiveWithContext(String meterSerial, byte[] request, long timeoutMs) {
        List<String> rxFrames = frames.getFrames();
        if (index >= rxFrames.size()) {
            throw new IllegalStateException("No more mock frames left for " + meterSerial);
        }

        String rxHex = rxFrames.get(index++);
        byte[] rxBytes = frames.hexToBytes(rxHex);

        log.info("[MOCK-TX]: {} -> {}", meterSerial, bytesToHex(request));
        log.info("[MOCK-RX]: {} <- {}", meterSerial, rxHex);

        return rxBytes;
    }

    public void reset() {
        this.index = 0;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}
