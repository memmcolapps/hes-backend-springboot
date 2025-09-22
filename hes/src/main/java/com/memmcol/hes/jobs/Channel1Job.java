package com.memmcol.hes.jobs;

import com.memmcol.hes.domain.profile.MetersLockService;
import com.memmcol.hes.service.MeterConnections;
import com.memmcol.hes.tasks.Channel1ProfileTask;
import com.netflix.discovery.converters.Auto;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@DisallowConcurrentExecution
@Component
public class Channel1Job extends QuartzJobBean {
    @Autowired
    private MetersLockService metersLockService;
    @Autowired
    @Qualifier("meterReadAdaptiveExecutor")
    private ExecutorService meterReadExecutor;

    public Channel1Job() {}

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        log.info("✅ Channel1Job executed at {}", context.getFireTime());

        List<String> activeMeters = new ArrayList<>(MeterConnections.getAllActiveSerials());
        for (String meter : activeMeters) {
            meterReadExecutor.submit(() -> {
                try {
                    metersLockService.readChannelOneWithLock("model", meter, "profileObis", 10);
                    log.info("Channel1ProfileTask for {}", meter);
                } catch (Exception e) {
                    log.error("❌ Failed to read Channel1 for {}", meter, e);
                }
            });
        }
    }
}
