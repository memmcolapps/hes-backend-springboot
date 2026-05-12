package com.memmcol.hes.jobs;

import com.memmcol.hes.domain.profile.MetersLockService;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@AllArgsConstructor
@NoArgsConstructor
@DisallowConcurrentExecution
public class HourlyProfileJob  extends AbstractObisProfileJob {
    @Autowired
    private MetersLockService metersLockService;

    @Override
    protected void runProfileJob(String model, String serial, String profileObis, int batchSize, JobExecutionContext context,boolean isMD) {
        metersLockService.readChannelOneWithLock(model, serial, profileObis, isMD);
        System.out.printf("⏰ HourlyProfileJob finished for meter %s%n", serial);
    }
}
