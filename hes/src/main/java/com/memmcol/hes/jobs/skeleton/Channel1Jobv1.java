package com.memmcol.hes.jobs.skeleton;

import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;

@Slf4j
@DisallowConcurrentExecution
@Component
public class Channel1Jobv1 extends QuartzJobBean {

    @Autowired
    private MetersLockServicev1 metersLockServicev1;

    @Autowired
    @Qualifier("meterReadAdaptiveExecutor")
    private ExecutorService meterReadExecutor;

    public Channel1Jobv1() {}

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        log.info("✅ Channel1Jobv1 executed at {}", context.getFireTime());

        List<String> meters = Arrays.asList("MTR1001", "MTR1002");
        for (String meter : meters) {
            meterReadExecutor.submit(() -> {
                try {
                    metersLockServicev1.readChannelOneWithLock("model", meter, "profileObis", 10);
                    log.info("Channel1ProfileTask for {}", meter);
                } catch (Exception e) {
                    log.error("❌ Failed for {}", meter, e);
                }
            });
        }

    }
}
