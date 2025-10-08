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
public class Channel1Job extends QuartzJobBean {
    @Autowired
    private ProfileExecutionService profileExecutionService;

    public Channel1Job(ProfileExecutionService profileExecutionService) {
        this.profileExecutionService = profileExecutionService;
    }

    //Required by Quartz
    public Channel1Job() {}

    @Override
    protected void executeInternal(JobExecutionContext context) {
        String obisCode = context.getMergedJobDataMap().getString("obisCodes");
        log.info("✅ Executing Channel1Job at {}, obis={}", context.getFireTime(), obisCode);
        profileExecutionService.readChannelOneForAll(obisCode); // <-- dynamic OBIS now
    }
}
