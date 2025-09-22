package com.memmcol.hes.tasks;

import com.memmcol.hes.domain.profile.MetersLockService;
import com.memmcol.hes.service.MeterConnections;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@AllArgsConstructor
public class Channel1ProfileTask implements Runnable {
    private final MetersLockService metersLockService;
    @Override
    public void run() {
        // Load profile channel 1 for all active meters
        List<String> activeMeters = new ArrayList<>(MeterConnections.getAllActiveSerials());
        for (String meter : activeMeters) {
            metersLockService.readChannelOneWithLock("model", "serial", "profileObis", 50);
            log.info("Channel1ProfileTask for {} ", meter);
        }
    }
}