package com.memmcol.hes.jobs;

import com.memmcol.hes.jobs.services.ProfileExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
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
    protected void executeInternal(JobExecutionContext context) {
        String obisCode = context.getMergedJobDataMap().getString("obisCodes");
        log.info("Executing Channel3JobHouseHold at {}, obis={}", context.getFireTime(), obisCode);
        profileExecutionService.readChannelThreeHouseholdForAll(obisCode);
    }
}
