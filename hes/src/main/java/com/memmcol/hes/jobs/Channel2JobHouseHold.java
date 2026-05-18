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
public class Channel2JobHouseHold extends QuartzJobBean {
    @Autowired
    private ProfileExecutionService profileExecutionService;

    public Channel2JobHouseHold(ProfileExecutionService profileExecutionService) {
        this.profileExecutionService = profileExecutionService;
    }

    public Channel2JobHouseHold() {}

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        log.info("START: Channel2Job-HH");
        String obisCode = context.getMergedJobDataMap().getString("obisCodes");
        QuartzExecutionLogging.logJobExecuteStart(log, context, obisCode);
        try {
            profileExecutionService.readChannelTwoHouseholdForAll(obisCode);
            log.info("COMPLETED: Channel2Job-HH");
        } catch (Exception e) {
            log.error("COMPLETED: Channel2Job-HH");
            QuartzExecutionLogging.logJobExecuteFailure(log, context, e);
            throw new JobExecutionException(e);
        }
    }
}
