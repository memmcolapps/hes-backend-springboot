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
public class Channel3JobHouseHold extends QuartzJobBean {
    @Autowired
    private ProfileExecutionService profileExecutionService;

    public Channel3JobHouseHold(ProfileExecutionService profileExecutionService) {
        this.profileExecutionService = profileExecutionService;
    }

    public Channel3JobHouseHold() {}

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        log.info("START: Channel3Job-HH");
        String obisCode = context.getMergedJobDataMap().getString("obisCodes");
        QuartzExecutionLogging.logJobExecuteStart(log, context, obisCode);
        try {
            profileExecutionService.readChannelThreeHouseholdForAll(obisCode);
            log.info("COMPLETED: Channel3Job-HH");
        } catch (Exception e) {
            log.error("ERROR: Channel3Job-HH");
            QuartzExecutionLogging.logJobExecuteFailure(log, context, e);
            throw new JobExecutionException(e);
        }
    }
}
