package com.memmcol.hes.job;

import com.memmcol.hes.domain.profile.MetersLockService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@AllArgsConstructor
@DisallowConcurrentExecution
public class HourlyProfileJob  extends AbstractObisProfileJob {
    private final MetersLockService metersLockService;

    @Override
    protected void runProfileJob(String model, String serial, String profileObis, int batchSize, JobExecutionContext context) {
        metersLockService.readChannelOneWithLock(model, serial, profileObis, batchSize);
        System.out.printf("⏰ HourlyProfileJob finished for meter %s%n", serial);
    }
}
