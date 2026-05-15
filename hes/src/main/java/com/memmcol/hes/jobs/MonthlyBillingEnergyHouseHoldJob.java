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
public class MonthlyBillingEnergyHouseHoldJob extends QuartzJobBean {
    @Autowired
    private ProfileExecutionService profileExecutionService;

    public MonthlyBillingEnergyHouseHoldJob(ProfileExecutionService profileExecutionService) {
        this.profileExecutionService = profileExecutionService;
    }

    public MonthlyBillingEnergyHouseHoldJob() {}

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        log.info("START: MonthlyBillingEnergyHouseHoldJob");
        String obisCode = context.getMergedJobDataMap().getString("obisCodes");
        QuartzExecutionLogging.logJobExecuteStart(log, context, obisCode);
        try {
            profileExecutionService.readMonthlyBillingEnergyHouseholdForAll(obisCode);
            log.info("COMPLETED: MonthlyBillingEnergyHouseHoldJob");
        } catch (Exception e) {
            log.error("ERROR: MonthlyBillingEnergyHouseHoldJob");
            QuartzExecutionLogging.logJobExecuteFailure(log, context, e);
            throw new JobExecutionException(e);
        }
    }
}
