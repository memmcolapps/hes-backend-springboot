package com.memmcol.hes.jobs;

import com.memmcol.hes.schedulers.QuartzExecutionLogging;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;

@Slf4j
public class SimpleJob extends QuartzJobBean {
    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        QuartzExecutionLogging.logJobExecuteStart(log, context);
        try {
            for (int i = 0; i < 5; i++) {
                log.info("hes.quartz.job phase=SIMPLE_TICK job=SimpleJob iteration={}", i);
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            QuartzExecutionLogging.logJobExecuteFailure(log, context, e);
            throw new JobExecutionException(e);
        } catch (Exception e) {
            QuartzExecutionLogging.logJobExecuteFailure(log, context, e);
            throw new JobExecutionException(e);
        }
    }
}
