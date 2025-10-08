package com.memmcol.hes.jobs.services;

import com.memmcol.hes.application.port.in.ProfileSyncUseCase;
import com.memmcol.hes.domain.profile.MetersLockService;
import com.memmcol.hes.dto.MeterDTO;
import com.memmcol.hes.model.MetersEntity;
import com.memmcol.hes.repository.MeterRepository;
import com.memmcol.hes.service.MeterConnections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Function;

@Service
@Slf4j
public class ProfileExecutionService {

    private final MetersLockService metersLockService;
    private final MeterRepository meterRepository;
    @Qualifier("meterReadAdaptiveExecutor")
    private final ExecutorService meterReadExecutor; // must be a Spring bean
    private final Duration perMeterTimeout = Duration.ofMinutes(10);

    public ProfileExecutionService(MetersLockService metersLockService, MeterRepository meterRepository, ExecutorService meterReadExecutor) {
        this.metersLockService = metersLockService;
        this.meterRepository = meterRepository;
        this.meterReadExecutor = meterReadExecutor;
    }

    /**
     * Generic helper to execute reads safely with timeout
     */

    /*TODO:
     */
    private void executeForAllMeters(String profileName,
                                     BiConsumer<MeterDTO, String> reader,
                                     String obisCode) {
        List<String> activeMeters = new ArrayList<>(MeterConnections.getAllActiveSerials());
        List<Future<Boolean>> futures = new ArrayList<>(activeMeters.size());

        for (String serial : activeMeters) {
            Future<Boolean> fut = meterReadExecutor.submit(() -> {
                try {

                    MeterDTO dto = meterRepository.findMeterDetailsByMeterNumber(serial)
                            .orElseThrow(() -> new IllegalArgumentException("Meter not found: " + serial));
                    dto.determineMD();

                    reader.accept(dto, obisCode);
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
    }

    // === Channel 1 ===
    public void readChannelOneForAll(String obisCode) {
        //Read
        executeForAllMeters("Channel1",
                (dto, obis) -> metersLockService.readChannelOneWithLock(
                        dto.getMeterModel(),
                        dto.getMeterNumber(),
                        obis,
                        dto.isMD()),
                obisCode);
    }

    // === Channel 2 ===
    public void readChannelTwoForAll(String obisCode) {
        executeForAllMeters("Channel2",
                (dto, obis) -> metersLockService.readChannelTwoWithLock(
                        dto.getMeterModel(),
                        dto.getMeterNumber(),
                        obis,
                        dto.isMD()),
                obisCode);
    }

    // === Events ===
    public void readEventsForAll(String obisCode) {
        executeForAllMeters("Events",
                (dto, obis) -> metersLockService.readEventsWithLock(
                        dto.getMeterModel(),
                        dto.getMeterNumber(),
                        obis,
                        dto.isMD()),
                obisCode);
    }

    // === Daily Billing ===
    public void readDailyBillingForAll(String obisCode) {
        executeForAllMeters("DailyBilling",
                (dto, obis) -> metersLockService.readDailyBillWithLock(
                        dto.getMeterModel(),
                        dto.getMeterNumber(),
                        obis,
                        dto.isMD()),
                obisCode);
    }

    // === Monthly Billing ===
    public void readMonthlyBillingForAll(String obisCode) {
        executeForAllMeters("MonthlyBilling",
                (dto, obis) -> metersLockService.readMonthlyBillWithLock(
                        dto.getMeterModel(),
                        dto.getMeterNumber(),
                        obis,
                        dto.isMD()),
                obisCode);
    }
}
