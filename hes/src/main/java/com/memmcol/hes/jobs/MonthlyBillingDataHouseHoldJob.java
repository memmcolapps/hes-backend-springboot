package com.memmcol.hes.jobs;

import com.memmcol.hes.jobs.services.ProfileExecutionService;
import com.memmcol.hes.schedulers.QuartzExecutionLogging;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

@Slf4j
@DisallowConcurrentExecution
@Component
public class MonthlyBillingDataHouseHoldJob extends QuartzJobBean {
    @Autowired
    private ProfileExecutionService profileExecutionService;

    public MonthlyBillingDataHouseHoldJob(ProfileExecutionService profileExecutionService) {
        this.profileExecutionService = profileExecutionService;
    }

    public MonthlyBillingDataHouseHoldJob() {}

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        log.info("START: MonthlyBillingDataHouseHoldJob");
        String obisCode = context.getMergedJobDataMap().getString("obisCodes");
        QuartzExecutionLogging.logJobExecuteStart(log, context, obisCode);
        try {
            profileExecutionService.readMonthlyBillingDataHouseholdForAll(obisCode);
            log.info("COMPLETED: MonthlyBillingDataHouseHoldJob");
        } catch (Exception e) {
            log.error("ERROR: MonthlyBillingDataHouseHoldJob");
            QuartzExecutionLogging.logJobExecuteFailure(log, context, e);
            throw new JobExecutionException(e);
        }
    }
}
