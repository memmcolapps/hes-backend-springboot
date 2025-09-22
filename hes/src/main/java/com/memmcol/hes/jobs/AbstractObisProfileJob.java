package com.memmcol.hes.jobs;

import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;

@Slf4j
@DisallowConcurrentExecution
public abstract class AbstractObisProfileJob extends QuartzJobBean {
    @Override
    protected void executeInternal(JobExecutionContext context) {
        String model = context.getMergedJobDataMap().getString("model");
        String serial = context.getMergedJobDataMap().getString("meterSerial");
        String profileObis = context.getMergedJobDataMap().getString("profileObis");
        int batchSize = context.getMergedJobDataMap().getInt("batchSize");

        log.info("▶ [{}] triggered for meter={}, model={}, obis={}",
                this.getClass().getSimpleName(), serial, model, profileObis);

        try {
            runProfileJob(model, serial, profileObis, batchSize, context);
        } catch (Exception ex) {
            log.error("❌ Error in [{}] for meter {}: {}",
                    this.getClass().getSimpleName(), serial, ex.getMessage());
        }
    }

    protected abstract void runProfileJob(
            String model, String serial, String profileObis, int batchSize, JobExecutionContext context);
}
