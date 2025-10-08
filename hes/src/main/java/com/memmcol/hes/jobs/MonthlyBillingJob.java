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
public class MonthlyBillingJob extends QuartzJobBean {
    @Autowired
    private ProfileExecutionService profileExecutionService;

    public MonthlyBillingJob(ProfileExecutionService profileExecutionService) {
        this.profileExecutionService = profileExecutionService;
    }

    //Required by Quartz
    public MonthlyBillingJob() {}

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        String obisCode = context.getMergedJobDataMap().getString("obisCodes");
        log.info("✅ Executing MonthlyBillingJob at {}, obis={}", context.getFireTime(), obisCode);
        profileExecutionService.readMonthlyBillingForAll(obisCode); // <-- dynamic OBIS now
    }
}
