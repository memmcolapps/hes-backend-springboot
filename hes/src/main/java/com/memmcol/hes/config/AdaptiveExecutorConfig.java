package com.memmcol.hes.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class AdaptiveExecutorConfig {

    @Bean(name = "meterReadAdaptiveExecutor")
    public ThreadPoolExecutor meterReadAdaptiveExecutor() {
        int cores = Runtime.getRuntime().availableProcessors();

        // start conservative, scale with load
        int corePoolSize = cores * 2;   // baseline
        int maxPoolSize  = cores * 10;  // upper bound

        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(1000); // tuneable

        return new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60L, TimeUnit.SECONDS,
                queue,
                new ThreadPoolExecutor.CallerRunsPolicy() // fallback if saturated
        );
    }
}
