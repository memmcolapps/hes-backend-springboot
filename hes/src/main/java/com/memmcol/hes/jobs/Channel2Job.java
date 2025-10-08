package com.memmcol.hes.jobs;

import com.memmcol.hes.jobs.services.ProfileExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
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
public class Channel2Job extends QuartzJobBean {
    @Autowired
    private ProfileExecutionService profileExecutionService;

    public Channel2Job(ProfileExecutionService profileExecutionService) {
        this.profileExecutionService = profileExecutionService;
    }

    //Required by Quartz
    public Channel2Job() {}

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        String obisCode = context.getMergedJobDataMap().getString("obisCodes");
        log.info("✅ Channel2Job executed at {}, obis={}", context.getFireTime(), obisCode);
        profileExecutionService.readChannelTwoForAll(obisCode); // batchSize can be externalized
    }
}
