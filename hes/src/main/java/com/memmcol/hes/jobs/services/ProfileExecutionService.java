package com.memmcol.hes.jobs.services;

import com.memmcol.hes.application.port.in.ProfileSyncUseCase;
import com.memmcol.hes.domain.profile.MetersLockService;
import com.memmcol.hes.repository.MeterRepository;
import com.memmcol.hes.service.MeterConnections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

@Service
@Slf4j
public class ProfileExecutionService {

    private final MetersLockService metersLockService;
    private final ProfileSyncUseCase syncUseCase;
    private final MeterRepository meterRepository;
    @Qualifier("meterReadAdaptiveExecutor")
    private final ExecutorService meterReadExecutor; // must be a Spring bean
    private final Duration perMeterTimeout = Duration.ofSeconds(30);

    public ProfileExecutionService(MetersLockService metersLockService, ProfileSyncUseCase syncUseCase, MeterRepository meterRepository, ExecutorService meterReadExecutor) {
        this.metersLockService = metersLockService;
        this.syncUseCase = syncUseCase;
        this.meterRepository = meterRepository;
        this.meterReadExecutor = meterReadExecutor;
    }

    /** Generic helper to execute reads safely with timeout */
    private int executeForAllMeters(String profileName,
                                    BiConsumer<String, Integer> reader,
                                    int batchSize) {
        List<String> activeMeters = new ArrayList<>(MeterConnections.getAllActiveSerials());
        List<Future<Boolean>> futures = new ArrayList<>(activeMeters.size());

        for (String serial : activeMeters) {
            Future<Boolean> fut = meterReadExecutor.submit(() -> {
                try {
                    reader.accept(serial, batchSize);
                    return true;
                } catch (Exception e) {
                    log.warn("{} read failed for {}: {}", profileName, serial, e.getMessage());
                    return false;
                }
            });
            futures.add(fut);
        }

        int success = 0;
        for (Future<Boolean> f : futures) {
            try {
                Boolean ok = f.get(perMeterTimeout.toMillis(), TimeUnit.MILLISECONDS);
                if (Boolean.TRUE.equals(ok)) success++;
            } catch (TimeoutException te) {
                f.cancel(true);
                log.warn("{} read timed out and was cancelled", profileName);
            } catch (Exception ex) {
                log.warn("{} read error: {}", profileName, ex.getMessage());
            }
        }

        log.info("{} read complete. success={}, total={}", profileName, success, activeMeters.size());
        return success;
    }

    // === Channel 1 ===
    public int readChannelOneForAll(int batchSize) {
        return executeForAllMeters("Channel1",
                (serial, size) -> metersLockService.readChannelOneWithLock("MMX-313-CT", serial, "1.0.99.1.0.255", size),
                batchSize);
    }

    // === Channel 2 ===
    public int readChannelTwoForAll(int batchSize) {
        return executeForAllMeters("Channel2",
                (serial, size) -> syncUseCase.syncUpToNow("model", serial, "0.0.2.0.0.255", size),
                batchSize);
    }

    // === Events ===
    public int readEventsForAll(int batchSize) {
        return executeForAllMeters("Events",
                (serial, size) -> metersLockService.readEventsWithLock("model", serial, "eventObis", size, false),
                batchSize);
    }

    // === Daily Billing ===
    public int readDailyBillingForAll(int batchSize) {
        return executeForAllMeters("DailyBilling",
                (serial, size) -> metersLockService.readDailyBillWithLock("model", serial, "1.0.98.1.0.255", size),
                batchSize);
    }

    // === Monthly Billing ===
    public int readMonthlyBillingForAll(int batchSize) {
        return executeForAllMeters("MonthlyBilling",
                (serial, size) -> metersLockService.readMonthlyBillWithLock("model", serial, "1.0.98.1.0.255", size),
                batchSize);
    }
}
