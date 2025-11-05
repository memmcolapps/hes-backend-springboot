package com.memmcol.hes.jobs;

import com.memmcol.hes.jobs.services.ProfileExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@DisallowConcurrentExecution
@Component
public class EventsJob extends QuartzJobBean {

    @Autowired
    private ProfileExecutionService profileExecutionService;

    public EventsJob(ProfileExecutionService profileExecutionService) {
        this.profileExecutionService = profileExecutionService;
    }

    //Required by Quartz
    public EventsJob() {}

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getMergedJobDataMap();
        String obisCodesStr = dataMap.getString("obisCodes");
        log.info("✅ EventsJob executed at {}", context.getFireTime());

        /*TODO:
        *  1. Event profile not consistent.
        *  2. Investigate why skipping some OBIS*/
        if (obisCodesStr != null) {
            String[] obisCodes = obisCodesStr.split(",");
            for (String obis : obisCodes) {
                log.info("🔎 Executing {} with obis={}", getClass().getSimpleName(), obis.trim());
                profileExecutionService.readEventsForAll(obis.trim());
            }
        }
    }
}
