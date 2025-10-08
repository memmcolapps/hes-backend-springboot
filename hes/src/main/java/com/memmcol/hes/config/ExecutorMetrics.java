package com.memmcol.hes.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadPoolExecutor;

@Component
public class ExecutorMetrics implements MeterBinder {

    private final ThreadPoolExecutor executor;

    public ExecutorMetrics(@Qualifier("meterReadAdaptiveExecutor") ThreadPoolExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("hes.executor.pool.size", executor, ThreadPoolExecutor::getPoolSize)
                .description("Current number of threads in meterReadExecutor pool")
                .register(registry);

        Gauge.builder("hes.executor.active.threads", executor, ThreadPoolExecutor::getActiveCount)
                .description("Number of active threads running meter tasks")
                .register(registry);

        Gauge.builder("hes.executor.queue.size", executor, e -> e.getQueue().size())
                .description("Number of meter tasks waiting in the queue")
                .register(registry);

        Gauge.builder("hes.executor.completed.tasks", executor, e -> e.getCompletedTaskCount())
                .description("Total number of completed meter tasks")
                .register(registry);
    }
}

