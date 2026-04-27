package com.memmcol.hes.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Collections;
import java.util.concurrent.ExecutorService;

@Configuration
public class ExecutorConfig {

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

    @Bean(name = "realtimeStreamExecutor")
    public ExecutorService realtimeStreamExecutor(@Value("${hes.realtime-read.stream-executor.size:10}") int poolSize) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("realtime-stream-");
        executor.initialize();

        return executor.getThreadPoolExecutor();
    }
}
