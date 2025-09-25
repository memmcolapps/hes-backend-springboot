package com.memmcol.hes.jobs;

import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@DisallowConcurrentExecution
public class DailyBillingJob extends QuartzJobBean {

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        log.info("✅ DailyBillingJob executed at {}", context.getFireTime());
        ExecutorService executor = Executors.newFixedThreadPool(5);
    }
}
