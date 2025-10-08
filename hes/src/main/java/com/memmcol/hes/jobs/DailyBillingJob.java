package com.memmcol.hes.jobs;

import com.memmcol.hes.jobs.services.ProfileExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@DisallowConcurrentExecution
@Component
public class DailyBillingJob extends QuartzJobBean {
    @Autowired
    private ProfileExecutionService profileExecutionService;

    public DailyBillingJob(ProfileExecutionService profileExecutionService) {
        this.profileExecutionService = profileExecutionService;
    }

    //Required by Quartz
    public DailyBillingJob() {}

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        String obisCode = context.getMergedJobDataMap().getString("obisCodes");
        log.info("✅ Executing DailyBillingJob at {}, obis={}", context.getFireTime(), obisCode);
        profileExecutionService.readDailyBillingForAll(obisCode); // <-- dynamic OBIS now
    }
}
