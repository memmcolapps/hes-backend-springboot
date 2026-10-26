package com.memmcol.hes.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.concurrent.ThreadPoolExecutor;

@Component
@Slf4j
public class ExecutorAutoscaler {

    private final ThreadPoolExecutor executor;
    private final int minPoolSize;
    private final int maxPoolSize;

    public ExecutorAutoscaler(@Qualifier("meterReadAdaptiveExecutor") ThreadPoolExecutor executor) {
        this.executor = executor;
        this.minPoolSize = Math.max(2, Runtime.getRuntime().availableProcessors() * 2);
        this.maxPoolSize = executor.getMaximumPoolSize();
    }

    @Scheduled(fixedRate = 5000) // check every 5s
    public void adjustPoolSize() {
        double load = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
        Runtime rt = Runtime.getRuntime();
        long freeMem = rt.freeMemory() + (rt.maxMemory() - rt.totalMemory());
        int queueSize = executor.getQueue().size();
        int currentCore = executor.getCorePoolSize();

        int newSize;

        if (queueSize > 500) {
            newSize = maxPoolSize;
        } else if (freeMem < (200 * 1024 * 1024)) {
            newSize = Math.max(currentCore - 5, minPoolSize);
        } else if (load < 0.7) {
            newSize = Math.min(currentCore + 5, maxPoolSize);
        } else {
            newSize = currentCore;
        }

        executor.setCorePoolSize(newSize);

        log.info("⚖️ Adjusted meterReadExecutor pool size to {}", newSize);
    }
}
