package com.memmcol.hes.jobs;

import com.memmcol.hes.domain.profile.MetersLockService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Slf4j
@DisallowConcurrentExecution
@Component
@AllArgsConstructor
public class DailyProfileJob extends AbstractObisProfileJob {

    /*TODO: (For information and development purpose)
    *  1. AbstractObisProfileJob parent to:
    *  2. DailyProfileJob
    *  3. HourlyProfileJob
    * */
    private final MetersLockService metersLockService;

    @Override
    protected void runProfileJob(String model, String serial, String profileObis, int batchSize, JobExecutionContext context) {
        metersLockService.readDailyBillWithLock(model, serial, profileObis, batchSize);
        log.info("📅 DailyProfileJob finished for meter {}", serial);
    }
}
