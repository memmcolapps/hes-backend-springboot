package com.memmcol.hes.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class AdaptiveExecutorConfig {

    @Value("${spring.datasource.hikari.maximum-pool-size:30}")
    private int hikariMaxPoolSize;

    @Bean(name = "meterReadAdaptiveExecutor")
    public ThreadPoolExecutor meterReadAdaptiveExecutor() {
        int cores = Runtime.getRuntime().availableProcessors();

        int dbBudget = Math.max(4, hikariMaxPoolSize - 6);
        int corePoolSize = Math.min(cores * 4, dbBudget);
        int maxPoolSize  = corePoolSize;

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
