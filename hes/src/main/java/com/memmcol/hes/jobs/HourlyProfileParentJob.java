package com.memmcol.hes.jobs;

import com.memmcol.hes.domain.profile.MetersLockService;
import com.memmcol.hes.schedulers.QuartzExecutionLogging;
import com.memmcol.hes.tasks.Channel1ProfileTask;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@DisallowConcurrentExecution
@Component
public class HourlyProfileParentJob extends QuartzJobBean {

    @Autowired
    private MetersLockService metersLockService;

    public HourlyProfileParentJob() {
    }

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        QuartzExecutionLogging.logJobExecuteStart(log, context);
        ExecutorService executor = Executors.newFixedThreadPool(5);
        try {
            executor.submit(new Channel1ProfileTask(metersLockService));
        } catch (Exception e) {
            QuartzExecutionLogging.logJobExecuteFailure(log, context, e);
            throw new JobExecutionException(e);
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
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
