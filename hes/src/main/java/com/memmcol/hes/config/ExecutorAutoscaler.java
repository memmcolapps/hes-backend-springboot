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

    public ExecutorAutoscaler(@Qualifier("meterReadAdaptiveExecutor") ThreadPoolExecutor executor) {
        this.executor = executor;
    }

    @Scheduled(fixedRate = 5000*3600) // check every 5s
    public void adjustPoolSize() {
        double load = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
        long freeMem = Runtime.getRuntime().freeMemory();
        int queueSize = executor.getQueue().size();

        int newSize;

        if (queueSize > 500) {
            newSize = executor.getMaximumPoolSize();
        } else if (load < 0.7 && freeMem > (200 * 1024 * 1024)) { // CPU < 70%, free mem > 200MB
            newSize = Math.min(executor.getPoolSize() + 5, executor.getMaximumPoolSize());
        } else {
            newSize = Math.max(executor.getPoolSize() - 5, executor.getCorePoolSize());
        }

        executor.setCorePoolSize(newSize);
        executor.setMaximumPoolSize(newSize);

        log.info("⚖️ Adjusted meterReadExecutor pool size to {}", newSize);
    }
}
