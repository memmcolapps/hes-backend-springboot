package com.memmcol.hes.job;

import com.memmcol.hes.domain.profile.MetersLockService;
import com.memmcol.hes.model.Meter;
import com.memmcol.hes.nettyUtils.RequestResponseService;
import com.memmcol.hes.repository.MeterRepository;
import com.memmcol.hes.service.MeterConnections;
import io.netty.handler.timeout.ReadTimeoutException;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
@DisallowConcurrentExecution
@Component
public class LoadProfileChannel1Job extends AbstractObisProfileJob {
    private final MetersLockService metersLockService;

    @Autowired
    public LoadProfileChannel1Job(RequestResponseService txRxService,
                                  MeterRepository meterRepo,
                                  MetersLockService metersLockService) {
        super(txRxService, meterRepo);
        this.metersLockService = metersLockService;
    }
    @Override
    protected void executeInternal(JobExecutionContext context) {
        executeProfile(context);
    }


    public void executeProfile(JobExecutionContext context) {
        log.info("LoadProfileChannel1Job started at {}", LocalDateTime.now());

        // Get all online active meter serials
        Set<String> activeSerials = MeterConnections.getAllActiveSerials();

        if (activeSerials.isEmpty()) {
            log.info("No active meters found. Exiting LoadProfileChannel1Job.");
            return; // Exit early
        }

        // Fetch Meter objects with model from DB
        List<Meter> meters = meterRepo.findByMeterNumbersIn(activeSerials);

        if (meters.isEmpty()) {
            log.warn("No meter records found in DB for active serials: {}", activeSerials);
            return;
        }

        // Batch execution using ExecutorService
        ExecutorService executor = Executors.newFixedThreadPool(50); // 50 meters per batch
        List<Future<Void>> futures = new ArrayList<>();

        /*TODO:
        *  1. Create a table to hold or profile OBIS codes and description
        *  2. I am considering using the table that contains the events profiles
        *  3. This will reduce hardcoding
        *   4. Remove executeProfileTestMode
        * */
        for (Meter meter : meters) {
            futures.add(executor.submit(() -> {
                try {
                    metersLockService.readEventsWithLock(
                            meter.getMeterModel(),
                            meter.getMeterNumber(),
                            "1.0.99.1.0.255",
                            50,
                            false);
                } catch (Exception e) {
                    log.error("Error reading meter={} : {}", meter.getMeterNumber(), e.getMessage(), e);
                }
                return null;
            }));
        }

        // Wait for all tasks to complete
        for (Future<Void> f : futures) {
            try {
                f.get(); // blocks until done
            } catch (Exception e) {
                log.error("Error in batch execution: {}", e.getMessage(), e);
            }
        }

        executor.shutdown();
        log.info("LoadProfileChannel1Job completed at {}", LocalDateTime.now());
    }

    public void executeProfileTestMode(JobExecutionContext context) {
        log.info("LoadProfileChannel1Jobs started at {}", LocalDateTime.now());

        // Get all online active meter serials
        Set<String> activeSerials = MeterConnections.getAllActiveSerials();

        if (activeSerials.isEmpty()) {
            log.info("No active meterss found. Exiting LoadProfileChannel1Job.");
            return; // Exit early
        }

        // Batch execution using ExecutorService
        ExecutorService executor = Executors.newFixedThreadPool(50); // 50 meters per batch
        List<Future<Void>> futures = new ArrayList<>();

        for (String meterNumber : activeSerials) {
            futures.add(executor.submit(() -> {
                try {
                    metersLockService.readEventsWithLock(
                            "MMX-313-CT",
                            meterNumber,
                            "1.0.99.1.0.255",
                            50,
                            false);
                } catch (Exception e) {
                    log.error("Error reading meter={} : {}", meterNumber, e.getMessage(), e);
                }
                return null;
            }));
        }

        // Wait for all tasks to complete
        for (Future<Void> f : futures) {
            try {
                f.get(); // blocks until done
            } catch (Exception e) {
                log.error("Error in batch execution: {}", e.getMessage(), e);
            }
        }

        executor.shutdown();
        log.info("LoadProfileChannel1Job 1 completed at {}", LocalDateTime.now());
    }

}
