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
public class ManageTokenEventJob extends QuartzJobBean {
    @Autowired
    private ProfileExecutionService profileExecutionService;

    public ManageTokenEventJob(ProfileExecutionService profileExecutionService) {
        this.profileExecutionService = profileExecutionService;
    }

    public ManageTokenEventJob() {}

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        log.info("START: ManageTokenEventJob");
        String obisCode = context.getMergedJobDataMap().getString("obisCodes");
        String obisHousehold = context.getMergedJobDataMap().getString("obisCodesHousehold");
        QuartzExecutionLogging.logJobExecuteStart(log, context, obisCode,
                "tieredProfile=management-token obisCodesHousehold=" + (obisHousehold == null ? "_none" : obisHousehold));
        try {
            profileExecutionService.readEventsWithMeterCategoryTiers(
                    EventScheduleProfile.MANAGEMENT_TOKEN, obisCode, obisHousehold);
            log.info("COMPLETED: ManageTokenEventJob");
        } catch (Exception e) {
            log.error("ERROR: ManageTokenEventJob");
            QuartzExecutionLogging.logJobExecuteFailure(log, context, e);
            throw new JobExecutionException(e);
        }
    }
}
