package com.memmcol.hes.jobs;

import com.memmcol.hes.domain.events.EventScheduleProfile;
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
public class FraudEventJob extends QuartzJobBean {
    @Autowired
    private ProfileExecutionService profileExecutionService;

    public FraudEventJob(ProfileExecutionService profileExecutionService) {
        this.profileExecutionService = profileExecutionService;
    }

    public FraudEventJob() {}

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        log.info("START: FraudEventJob");
        String obisCode = context.getMergedJobDataMap().getString("obisCodes");
        String obisHousehold = context.getMergedJobDataMap().getString("obisCodesHousehold");
        QuartzExecutionLogging.logJobExecuteStart(log, context, obisCode,
                "tieredProfile=fraud-event obisCodesHousehold=" + (obisHousehold == null ? "_none" : obisHousehold));
        try {
            profileExecutionService.readEventsWithMeterCategoryTiers(
                    EventScheduleProfile.FRAUD_EVENT, obisCode, obisHousehold);
            log.info("COMPLETED: FraudEventJob");
        } catch (Exception e) {
            log.error("ERROR: FraudEventJob");
            QuartzExecutionLogging.logJobExecuteFailure(log, context, e);
            throw new JobExecutionException(e);
        }
    }
}
