package com.memmcol.hes.jobs.services;

import com.memmcol.hes.domain.profile.MetersLockService;
import com.memmcol.hes.dto.MeterDTO;
import com.memmcol.hes.repository.MeterRepository;
import com.memmcol.hes.service.MeterConnections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ProfileExecutionService {

    private final MetersLockService metersLockService;
    private final MeterRepository meterRepository;
    @Qualifier("meterReadAdaptiveExecutor")
    private final ExecutorService meterReadExecutor; // must be a Spring bean
    private final Duration perMeterTimeout = Duration.ofMinutes(10);
    private final int meterBatchSize;
    private final ZoneId executionWindowZone;
    private final LocalTime executionWindowStart;
    private final LocalTime executionWindowEnd;
    private final boolean executionWindowEnabled;
    private final Set<String> householdMeterModels;

    public ProfileExecutionService(MetersLockService metersLockService,
                                   MeterRepository meterRepository,
                                   @Qualifier("meterReadAdaptiveExecutor") ExecutorService meterReadExecutor,
                                   @Value("${hes.profile.execution.batch-size:${hes.meter.executor.size:50}}") int meterBatchSize,
                                   @Value("${hes.profile.execution.window.zone:Africa/Lagos}") String executionWindowZone,
                                   @Value("${hes.profile.execution.window.start:22:00}") String executionWindowStart,
                                   @Value("${hes.profile.execution.window.end:06:00}") String executionWindowEnd,
                                    @Value("${hes.profile.execution.window.enabled:true}") boolean executionWindowEnabled,
                                    @Value("${hes.profile.household.models:}") String householdModelsCsv) {
        this.metersLockService = metersLockService;
        this.meterRepository = meterRepository;
        this.meterReadExecutor = meterReadExecutor;
        this.meterBatchSize = Math.max(1, meterBatchSize);
        this.executionWindowZone = ZoneId.of(executionWindowZone);
        this.executionWindowStart = LocalTime.parse(executionWindowStart);
        this.executionWindowEnd = LocalTime.parse(executionWindowEnd);
        this.executionWindowEnabled = executionWindowEnabled;
        this.householdMeterModels = parseCsvSet(householdModelsCsv);
    }

    private static Set<String> parseCsvSet(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Generic helper to execute reads safely with timeout
     */

    /*TODO:
     */
    private void executeForAllMeters(String profileName,
                                     BiConsumer<MeterDTO, String> reader,
                                     String obisCode) {
        executeForAllMeters(profileName, reader, obisCode, null);
    }

    private void executeForAllMeters(String profileName,
                                     BiConsumer<MeterDTO, String> reader,
                                     String obisCode,
                                     Set<String> allowedModels) {
        if (!isWithinExecutionWindow()) {
            log.info("{} read skipped. Current time is outside configured execution window {}-{} {}.",
                    profileName, executionWindowStart, executionWindowEnd, executionWindowZone);
            return;
        }

        List<String> activeMeters = new ArrayList<>(MeterConnections.getAllActiveSerials());

        if (activeMeters.isEmpty()) {
            log.info("{} read skipped. No active meters found.", profileName);
            return;
        }

        int missing = 0;
        int success = 0;
        int failed = 0;
        int timedOut = 0;

        log.info("{} read starting. activeMeters={}, batchSize={}",
                profileName, activeMeters.size(), meterBatchSize);

        for (int from = 0; from < activeMeters.size(); from += meterBatchSize) {
            if (!isWithinExecutionWindow()) {
                log.info("{} read paused at meter offset {}. Execution window {}-{} {} has closed.",
                        profileName, from, executionWindowStart, executionWindowEnd, executionWindowZone);
                break;
            }

            int to = Math.min(from + meterBatchSize, activeMeters.size());
            List<String> serialBatch = activeMeters.subList(from, to);

            Map<String, MeterDTO> meterDetailsBySerial = meterRepository.findMeterDetailsByMeterNumberIn(serialBatch)
                    .stream()
                    .peek(MeterDTO::determineMD)
                    .collect(Collectors.toMap(
                            MeterDTO::getMeterNumber,
                            dto -> dto,
                            (existing, replacement) -> existing
                    ));

            Set<String> missingSerials = new HashSet<>(serialBatch);
            missingSerials.removeAll(meterDetailsBySerial.keySet());
            missing += missingSerials.size();
            if (!missingSerials.isEmpty()) {
                log.warn("{} read skipped {} meter(s) missing DB details in batch {}-{}",
                        profileName, missingSerials.size(), from + 1, to);
            }

            List<MeterDTO> metersToRead = serialBatch.stream()
                    .map(meterDetailsBySerial::get)
                    .filter(dto -> dto != null)
                    .filter(dto -> allowedModels == null || allowedModels.isEmpty() || allowedModels.contains(dto.getMeterModel()))
                    .toList();

            BatchResult batchResult = executeBatch(profileName, metersToRead, obisCode, reader);
            success += batchResult.success();
            failed += batchResult.failed();
            timedOut += batchResult.timedOut();

            log.info("{} batch complete. range={}-{}, success={}, failed={}, timeout={}, missing={}",
                    profileName, from + 1, to, batchResult.success(), batchResult.failed(),
                    batchResult.timedOut(), missingSerials.size());
        }

        log.info("{} read complete. success={}, failed={}, timeout={}, missing={}, total={}",
                profileName, success, failed, timedOut, missing, activeMeters.size());
    }

    private boolean isWithinExecutionWindow() {
        if (!executionWindowEnabled) {
            return true;
        }

        LocalTime now = ZonedDateTime.now(executionWindowZone).toLocalTime();
        if (executionWindowStart.equals(executionWindowEnd)) {
            return true;
        }

        if (executionWindowStart.isBefore(executionWindowEnd)) {
            return !now.isBefore(executionWindowStart) && now.isBefore(executionWindowEnd);
        }

        return !now.isBefore(executionWindowStart) || now.isBefore(executionWindowEnd);
    }

    private BatchResult executeBatch(String profileName,
                                     List<MeterDTO> metersToRead,
                                     String obisCode,
                                     BiConsumer<MeterDTO, String> reader) {
        CompletionService<MeterReadResult> completionService = new ExecutorCompletionService<>(meterReadExecutor);
        List<Future<MeterReadResult>> submitted = new ArrayList<>(metersToRead.size());

        for (MeterDTO dto : metersToRead) {
            submitted.add(completionService.submit(() -> readMeter(profileName, dto, obisCode, reader)));
        }

        return awaitBatch(profileName, completionService, submitted);
    }

    private MeterReadResult readMeter(String profileName,
                                      MeterDTO dto,
                                      String obisCode,
                                      BiConsumer<MeterDTO, String> reader) {
        try {
            reader.accept(dto, obisCode);
            return MeterReadResult.success(dto.getMeterNumber());
        } catch (Exception e) {
            log.warn("{} read failed for {}: {}", profileName, dto.getMeterNumber(), e.getMessage());
            return MeterReadResult.failed(dto.getMeterNumber());
        }
    }

    private BatchResult awaitBatch(String profileName,
                                   CompletionService<MeterReadResult> completionService,
                                   List<Future<MeterReadResult>> submitted) {
        int success = 0;
        int failed = 0;
        int completed = 0;
        long deadlineNanos = System.nanoTime() + perMeterTimeout.toNanos();

        while (completed < submitted.size()) {
            try {
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0) {
                    break;
                }

                Future<MeterReadResult> future = completionService.poll(remainingNanos, TimeUnit.NANOSECONDS);
                if (future == null) {
                    break;
                }

                completed++;
                MeterReadResult result = future.get();
                if (result.success()) {
                    success++;
                } else {
                    failed++;
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException ex) {
                failed++;
                log.warn("{} read error: {}", profileName, ex.getMessage());
            }
        }

        int timedOut = submitted.size() - completed;
        if (timedOut > 0) {
            submitted.stream()
                    .filter(future -> !future.isDone())
                    .forEach(future -> future.cancel(true));
            log.warn("{} batch timed out. cancelled {} pending meter read(s)", profileName, timedOut);
        }

        return new BatchResult(success, failed, timedOut);
    }

    private record MeterReadResult(String serial, boolean success) {
        private static MeterReadResult success(String serial) {
            return new MeterReadResult(serial, true);
        }

        private static MeterReadResult failed(String serial) {
            return new MeterReadResult(serial, false);
        }
    }

    private record BatchResult(int success, int failed, int timedOut) {
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

    public void readChannelOneHouseholdForAll(String obisCode) {
        if (householdMeterModels.isEmpty()) {
            log.warn("Channel1Household read skipped. No models configured in hes.profile.household.models");
            return;
        }
        executeForAllMeters("Channel1Household",
                (dto, obis) -> metersLockService.readChannelOneHouseholdWithLock(
                        dto.getMeterModel(),
                        dto.getMeterNumber(),
                        obis,
                        dto.isMD()),
                obisCode,
                householdMeterModels);
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

    public void readChannelTwoHouseholdForAll(String obisCode) {
        if (householdMeterModels.isEmpty()) {
            log.warn("Channel2Household read skipped. No models configured in hes.profile.household.models");
            return;
        }
        executeForAllMeters("Channel2Household",
                (dto, obis) -> metersLockService.readChannelTwoHouseholdWithLock(
                        dto.getMeterModel(),
                        dto.getMeterNumber(),
                        obis,
                        dto.isMD()),
                obisCode,
                householdMeterModels);
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

    public void readDailyBillingDataHouseholdForAll(String obisCode) {
        if (householdMeterModels.isEmpty()) {
            log.warn("DailyBillingDataHousehold read skipped. No models configured in hes.profile.household.models");
            return;
        }
        executeForAllMeters("DailyBillingDataHousehold",
                (dto, obis) -> metersLockService.readDailyBillingDataHouseholdWithLock(
                        dto.getMeterModel(),
                        dto.getMeterNumber(),
                        obis,
                        dto.isMD()),
                obisCode,
                householdMeterModels);
    }

    public void readDailyBillingEnergyHouseholdForAll(String obisCode) {
        if (householdMeterModels.isEmpty()) {
            log.warn("DailyBillingEnergyHousehold read skipped. No models configured in hes.profile.household.models");
            return;
        }
        executeForAllMeters("DailyBillingEnergyHousehold",
                (dto, obis) -> metersLockService.readDailyBillingEnergyHouseholdWithLock(
                        dto.getMeterModel(),
                        dto.getMeterNumber(),
                        obis,
                        dto.isMD()),
                obisCode,
                householdMeterModels);
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

    public void readMonthlyBillingDataHouseholdForAll(String obisCode) {
        if (householdMeterModels.isEmpty()) {
            log.warn("MonthlyBillingDataHousehold read skipped. No models configured in hes.profile.household.models");
            return;
        }
        executeForAllMeters("MonthlyBillingDataHousehold",
                (dto, obis) -> metersLockService.readMonthlyBillingDataHouseholdWithLock(
                        dto.getMeterModel(),
                        dto.getMeterNumber(),
                        obis,
                        dto.isMD()),
                obisCode,
                householdMeterModels);
    }

    public void readMonthlyBillingEnergyHouseholdForAll(String obisCode) {
        if (householdMeterModels.isEmpty()) {
            log.warn("MonthlyBillingEnergyHousehold read skipped. No models configured in hes.profile.household.models");
            return;
        }
        executeForAllMeters("MonthlyBillingEnergyHousehold",
                (dto, obis) -> metersLockService.readMonthlyBillingEnergyHouseholdWithLock(
                        dto.getMeterModel(),
                        dto.getMeterNumber(),
                        obis,
                        dto.isMD()),
                obisCode,
                householdMeterModels);
    }
}
