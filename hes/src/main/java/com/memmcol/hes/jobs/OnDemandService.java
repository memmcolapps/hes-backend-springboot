package com.memmcol.hes.jobs;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class OnDemandService {
    private final ExecutorService urgentExecutor = Executors.newFixedThreadPool(3);

    public void readObis(String meterSerial, String obisCode) {
        urgentExecutor.submit(() -> {
//            ProfileReader.readObis(meterSerial, obisCode);
            log.info("Reading obis code: {} for meter serial {}", obisCode, meterSerial);
        });
    }
}
