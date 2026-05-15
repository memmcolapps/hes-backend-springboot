package com.memmcol.hes.jobs;

import com.memmcol.hes.schedulers.QuartzExecutionLogging;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@DisallowConcurrentExecution
public class HourlyJobs extends QuartzJobBean {
    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        QuartzExecutionLogging.logJobExecuteStart(log, context);
        ExecutorService executor = Executors.newFixedThreadPool(5);
        try {
            log.debug("hes.quartz.job phase=HOURLY_JOBS_NOOP job=HourlyJobs (executor ready, no inline tasks)");
        } catch (Exception e) {
            QuartzExecutionLogging.logJobExecuteFailure(log, context, e);
            throw new JobExecutionException(e);
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException ie) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
                throw new JobExecutionException(ie);
            }
        }
    }
}
