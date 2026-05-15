package com.memmcol.hes.jobs;

import com.memmcol.hes.schedulers.QuartzExecutionLogging;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;

@Slf4j
@DisallowConcurrentExecution
public class SampleCronJob extends QuartzJobBean {
    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        QuartzExecutionLogging.logJobExecuteStart(log, context);
        try {
            for (int i = 0; i < 10; i++) {
                log.info("hes.quartz.job phase=SAMPLE_TICK job=SampleCronJob iteration={}", i);
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
