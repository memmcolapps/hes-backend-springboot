package com.memmcol.hes.jobs.skeleton;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

//@Configuration
public class ExecutorConfig {
    @Bean(name = "meterReadExecutor")
    public ExecutorService meterReadExecutor() {
        return Executors.newFixedThreadPool(5);
    }
}
