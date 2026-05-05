package com.memmcol.hes.jobs;

import com.memmcol.hes.jobs.services.ProfileExecutionService;
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
public class HouseholdEventJob extends QuartzJobBean {
    @Autowired
    private ProfileExecutionService profileExecutionService;

    public HouseholdEventJob(ProfileExecutionService profileExecutionService) {
        this.profileExecutionService = profileExecutionService;
    }

    //Required by Quartz
    public HouseholdEventJob() {}

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        String obisCodesStr = context.getMergedJobDataMap().getString("obisCodes");
        log.info("✅ Executing HouseholdEventJob at {}, obis={}", context.getFireTime(), obisCodesStr);

        if (obisCodesStr == null || obisCodesStr.isBlank()) {
            log.warn("HouseholdEventJob skipped: missing obisCodes");
            return;
        }

        String[] obisCodes = obisCodesStr.split(",");
        for (String obis : obisCodes) {
            String trimmed = obis.trim();
            if (trimmed.isBlank()) continue;
            profileExecutionService.readEventsHouseholdForAll(trimmed);
        }
    }
}

