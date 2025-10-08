package com.memmcol.hes.jobs;

import com.memmcol.hes.domain.profile.MetersLockService;
import com.memmcol.hes.tasks.Channel1ProfileTask;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@AllArgsConstructor
@DisallowConcurrentExecution
/*
* This job is expected to run every one hour, and it will execute all the listed profiles runnable
* */
public class HourlyProfileParentJob extends QuartzJobBean {
    private final MetersLockService metersLockService;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        log.info("✅ EventsJob executed at {}", context.getFireTime());
        ExecutorService executor = Executors.newFixedThreadPool(5);

        // Schedule subtasks in parallel
        executor.submit(new Channel1ProfileTask(metersLockService));
//        executor.submit(new Channel2Job());
//        executor.submit(new EventsJob("standard"));
//        executor.submit(new EventsJob("power-grid"));
//        executor.submit(new EventsJob("fraud"));
//        executor.submit(new EventsJob("control"));

        executor.shutdown();
    }
}
