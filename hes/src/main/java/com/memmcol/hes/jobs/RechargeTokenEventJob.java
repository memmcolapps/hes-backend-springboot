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
public class RechargeTokenEventJob extends QuartzJobBean {

    @Autowired
    private ProfileExecutionService profileExecutionService;

    public RechargeTokenEventJob(ProfileExecutionService profileExecutionService) {
        this.profileExecutionService = profileExecutionService;
    }

    public RechargeTokenEventJob() {}

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        log.info("START: RechargeTokenEventJob");
        String obisCode = context.getMergedJobDataMap().getString("obisCodes");
        String obisHousehold = context.getMergedJobDataMap().getString("obisCodesHousehold");
        QuartzExecutionLogging.logJobExecuteStart(log, context, obisCode,
                "tieredProfile=recharge-token obisCodesHousehold=" + (obisHousehold == null ? "_none" : obisHousehold));
        try {
            profileExecutionService.readEventsWithMeterCategoryTiers(
                    EventScheduleProfile.RECHARGE_TOKEN, obisCode, obisHousehold);
            log.info("COMPLETED: RechargeTokenEventJob");
        } catch (Exception e) {
            log.error("ERROR: RechargeTokenEventJob");
            QuartzExecutionLogging.logJobExecuteFailure(log, context, e);
            throw new JobExecutionException(e);
        }
    }
}
