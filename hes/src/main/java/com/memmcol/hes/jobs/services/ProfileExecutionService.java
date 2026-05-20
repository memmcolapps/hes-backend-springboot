package com.memmcol.hes.jobs.services;

import com.memmcol.hes.application.port.out.EventObisResolutionPort;
import com.memmcol.hes.domain.events.EventScheduleProfile;
import com.memmcol.hes.domain.events.ResolvedTieredEventObis;
import com.memmcol.hes.domain.profile.MetersLockService;
import com.memmcol.hes.domain.profile.ProfileMeterEligibility;
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
import java.util.function.Predicate;
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
    private final EventObisResolutionPort eventObisResolutionPort;

    public ProfileExecutionService(MetersLockService metersLockService,
                                   MeterRepository meterRepository,
                                   @Qualifier("meterReadAdaptiveExecutor") ExecutorService meterReadExecutor,
                                   EventObisResolutionPort eventObisResolutionPort,
                                   @Value("${hes.profile.execution.batch-size:${hes.meter.executor.size:50}}") int meterBatchSize,
                                   @Value("${hes.profile.execution.window.zone:Africa/Lagos}") String executionWindowZone,
                                   @Value("${hes.profile.execution.window.start:22:00}") String executionWindowStart,
                                   @Value("${hes.profile.execution.window.end:06:00}") String executionWindowEnd,
                                    @Value("${hes.profile.execution.window.enabled:true}") boolean executionWindowEnabled,
                                    @Value("${hes.profile.household.models:}") String householdModelsCsv) {
        this.metersLockService = metersLockService;
        this.meterRepository = meterRepository;
        this.meterReadExecutor = meterReadExecutor;
        this.eventObisResolutionPort = eventObisResolutionPort;
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

    private Set<String> excludeHouseholdModels() {
        return ProfileMeterEligibility.householdModelsToExclude(householdMeterModels);
    }

    private Predicate<MeterDTO> excludeMdClassForHouseholdJobs() {
        return ProfileMeterEligibility.excludeMdClassMeters();
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
        executeForAllMeters(profileName, reader, obisCode, allowedModels, null, null);
    }

    /**
     * @param excludedModels when non-null and non-empty, meters whose {@link MeterDTO#getMeterModel()} is in this set are skipped
     */
    private void executeForAllMeters(String profileName,
                                     BiConsumer<MeterDTO, String> reader,
                                     String obisCode,
                                     Set<String> allowedModels,
                                     Set<String> excludedModels) {
        executeForAllMeters(profileName, reader, obisCode, allowedModels, excludedModels, null);
    }

    /**
     * @param meterFilter when non-null, only meters matching this predicate are read (applied after model allow/exclude)
     */
    private void executeForAllMeters(String profileName,
                                     BiConsumer<MeterDTO, String> reader,
                                     String obisCode,
                                     Set<String> allowedModels,
                                     Set<String> excludedModels,
                                     Predicate<MeterDTO> meterFilter) {
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
                    .filter(dto -> excludedModels == null || excludedModels.isEmpty() || !excludedModels.contains(dto.getMeterModel()))
                    .filter(dto -> meterFilter == null || meterFilter.test(dto))
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
        log.info(
                "hes.profile.batch summary profileName={} obis={} metersSucceeded={} metersFailed={} metersTimedOut={} metersMissingDb={} totalActiveSerials={} note=Per_meter_fact_table_persistence_is_logged_separately_with_prefix_Fact_DB_persistence_and_hes.factdb_outcome",
                profileName, obisCode, success, failed, timedOut, missing, activeMeters.size());
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
                obisCode,
                null,
                excludeHouseholdModels());
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
                householdMeterModels,
                null,
                excludeMdClassForHouseholdJobs());
    }

    // === Channel 2 ===
    public void readChannelTwoForAll(String obisCode) {
        executeForAllMeters("Channel2",
                (dto, obis) -> metersLockService.readChannelTwoWithLock(
                        dto.getMeterModel(),
                        dto.getMeterNumber(),
                        obis,
                        dto.isMD()),
                obisCode,
                null,
                excludeHouseholdModels());
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
                householdMeterModels,
                null,
                excludeMdClassForHouseholdJobs());
    }

    public void readChannelThreeHouseholdForAll(String obisCode) {
        if (householdMeterModels.isEmpty()) {
            log.warn("Channel3Household read skipped. No models configured in hes.profile.household.models");
            return;
        }
        executeForAllMeters("Channel3Household",
                (dto, obis) -> metersLockService.readChannelThreeHouseholdWithLock(
                        dto.getMeterModel(),
                        dto.getMeterNumber(),
                        obis,
                        dto.isMD()),
                obisCode,
                householdMeterModels,
                null,
                excludeMdClassForHouseholdJobs());
    }

    // === Events ===

    /**
     * Shared two-column event profiles (standard, power grid, etc.) persisted to {@code event_log}.
     * Fraud/control use {@link #readEventsWithMeterCategoryTiers} so household meters get dedicated tables.
     */
    public void readEventsForAll(String obisCode) {
        executeForAllMeters("Events",
                (dto, obis) -> metersLockService.readEventsWithLock(
                        dto.getMeterModel(),
                        dto.getMeterNumber(),
                        obis,
                        dto.isMD()),
                obisCode);
    }

    /**
     * Tiered event reads: MD/CT → {@code event_log}; household → dedicated tables (token, fraud, control).
     * Optional MD/CT tier runs when {@code md-ct} OBIS is configured; household tier uses
     * {@code hes.profile.household.models}.
     */
    public void readEventsWithMeterCategoryTiers(EventScheduleProfile profile,
                                                 String schedulerObisMdCt,
                                                 String schedulerObisHouseholdOverride) {
        ResolvedTieredEventObis resolved = eventObisResolutionPort.resolve(
                profile, schedulerObisMdCt, schedulerObisHouseholdOverride);
        log.info(
                "hes.events tiered_schedule profile={} mdCtObis={} householdObis={} householdModelCount={}",
                profile.configKey(),
                resolved.mdCtObis() == null || resolved.mdCtObis().isBlank() ? "_none" : resolved.mdCtObis(),
                resolved.householdObis() == null || resolved.householdObis().isBlank() ? "_none" : resolved.householdObis(),
                householdMeterModels.size());

        BiConsumer<MeterDTO, String> mdCtReader = (dto, obis) -> metersLockService.readEventsWithLock(
                dto.getMeterModel(),
                dto.getMeterNumber(),
                obis,
                dto.isMD());

        BiConsumer<MeterDTO, String> householdReader = householdEventReader(profile);

        if (resolved.mdCtObis() == null || resolved.mdCtObis().isBlank()) {
            log.info(
                    "hes.events tiered_schedule profile={} phase=MD_CT_SKIPPED reason=no_md_ct_obis",
                    profile.configKey());
        } else {
            // MD/CT tier → event_log; exclude household models so they are not double-read when OBIS overlaps
            executeForAllMeters(
                    "Events." + profile.configKey() + ".mdCt",
                    mdCtReader,
                    resolved.mdCtObis(),
                    null,
                    excludeHouseholdModels());
        }

        if (householdMeterModels.isEmpty()) {
            log.warn(
                    "hes.events tiered_schedule profile={} phase=HOUSEHOLD_SKIPPED reason=no_household_models (hes.profile.household.models empty)",
                    profile.configKey());
            return;
        }
        if (resolved.householdObis() == null || resolved.householdObis().isBlank()) {
            log.warn(
                    "hes.events tiered_schedule profile={} phase=HOUSEHOLD_SKIPPED reason=no_household_obis (set scheduler obisCodesHousehold or hes.events.profiles.{}.household)",
                    profile.configKey(), profile.configKey());
            return;
        }
        executeForAllMeters(
                "Events." + profile.configKey() + ".household",
                householdReader,
                resolved.householdObis(),
                householdMeterModels);
    }

    /**
     * Household event profiles use dedicated tables (not {@code event_log}).
     */
    private BiConsumer<MeterDTO, String> householdEventReader(EventScheduleProfile profile) {
        return switch (profile) {
            case RECHARGE_TOKEN -> (dto, obis) -> metersLockService.readHouseholdRechargeTokenEventsWithLock(
                    dto.getMeterModel(), dto.getMeterNumber(), obis, dto.isMD());
            case MANAGEMENT_TOKEN -> (dto, obis) -> metersLockService.readHouseholdManagementTokenEventsWithLock(
                    dto.getMeterModel(), dto.getMeterNumber(), obis, dto.isMD());
            case FRAUD_EVENT -> (dto, obis) -> metersLockService.readHouseholdFraudEventsWithLock(
                    dto.getMeterModel(), dto.getMeterNumber(), obis, dto.isMD());
            case CONTROL_EVENT -> (dto, obis) -> metersLockService.readHouseholdControlEventsWithLock(
                    dto.getMeterModel(), dto.getMeterNumber(), obis, dto.isMD());
        };
    }

    // === Daily Billing ===
    public void readDailyBillingForAll(String obisCode) {
        executeForAllMeters("DailyBilling",
                (dto, obis) -> metersLockService.readDailyBillWithLock(
                        dto.getMeterModel(),
                        dto.getMeterNumber(),
                        obis,
                        dto.isMD()),
                obisCode,
                null,
                excludeHouseholdModels());
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
                householdMeterModels,
                null,
                excludeMdClassForHouseholdJobs());
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
                householdMeterModels,
                null,
                excludeMdClassForHouseholdJobs());
    }

    // === Monthly Billing ===
    public void readMonthlyBillingForAll(String obisCode) {
        executeForAllMeters("MonthlyBilling",
                (dto, obis) -> metersLockService.readMonthlyBillWithLock(
                        dto.getMeterModel(),
                        dto.getMeterNumber(),
                        obis,
                        dto.isMD()),
                obisCode,
                null,
                excludeHouseholdModels());
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
                householdMeterModels,
                null,
                excludeMdClassForHouseholdJobs());
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
                householdMeterModels,
                null,
                excludeMdClassForHouseholdJobs());
    }
}
