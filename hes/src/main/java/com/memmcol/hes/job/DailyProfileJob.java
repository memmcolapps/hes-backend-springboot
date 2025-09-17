package com.memmcol.hes.job;

import com.memmcol.hes.domain.profile.MetersLockService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@DisallowConcurrentExecution
@Component
@AllArgsConstructor
public class DailyProfileJob extends AbstractObisProfileJob {

    private final MetersLockService metersLockService;

    @Override
    protected void runProfileJob(String model, String serial, String profileObis, int batchSize, JobExecutionContext context) {
        metersLockService.readDailyBillWithLock(model, serial, profileObis, batchSize);
        log.info("📅 DailyProfileJob finished for meter {}", serial);
    }
}
