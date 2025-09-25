package com.memmcol.hes.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ExecutorConfig {

    @Value("${hes.meter.executor.size:50}") // fallback to 50 if not set
    private int poolSize;

    @Bean(name = "meterReadExecutor")
    public ExecutorService meterReadExecutor(MeterRegistry registry,
                                             @Value("${hes.meter.executor.size:50}") int poolSize) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("meter-read-");
        executor.initialize();

        ExecutorService executorService = executor.getThreadPoolExecutor();

        ExecutorServiceMetrics.monitor(
                registry,
                executorService,
                "meter.read.executor",
                Collections.emptyList()
        );

        return executorService;
    }
}