package com.memmcol.hes.jobs;

import com.memmcol.hes.schedulers.QuartzExecutionLogging;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;

@Slf4j
@DisallowConcurrentExecution
public abstract class AbstractObisProfileJob extends QuartzJobBean {
    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        String model = context.getMergedJobDataMap().getString("model");
        String serial = context.getMergedJobDataMap().getString("meterSerial");
        String profileObis = context.getMergedJobDataMap().getString("profileObis");
        int batchSize = context.getMergedJobDataMap().getInt("batchSize");
        boolean isMD = context.getMergedJobDataMap().getBoolean("isMD");

        QuartzExecutionLogging.logJobExecuteStart(
                log, context, profileObis,
                "meterSerial=" + serial + " model=" + model + " batchSize=" + batchSize + " isMD=" + isMD);

        try {
            runProfileJob(model, serial, profileObis, batchSize, context, isMD);
        } catch (Exception ex) {
            QuartzExecutionLogging.logJobExecuteFailure(log, context, ex);
            throw new JobExecutionException(ex);
        }
    }

    protected abstract void runProfileJob(
            String model, String serial, String profileObis, int batchSize, JobExecutionContext context, boolean isMD);
}
