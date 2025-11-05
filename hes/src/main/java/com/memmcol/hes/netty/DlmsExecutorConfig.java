package com.memmcol.hes.netty;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class DlmsExecutorConfig {
    @Bean
    @Qualifier("dlmsScheduledExecutor")
    public ScheduledExecutorService dlmsScheduledExecutor() {
        return Executors.newScheduledThreadPool(5, r -> {
            Thread t = new Thread(r);
            t.setName("DLMS-Scheduler-" + t.getId());
            t.setDaemon(true);
            return t;
        });
    }
}