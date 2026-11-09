package com.memmcol.hes.schedulers;

import com.memmcol.hes.application.port.out.CapturePeriodPort;
import com.memmcol.hes.application.port.out.MeterLockPort;
import com.memmcol.hes.domain.profile.*;
import com.memmcol.hes.domain.profile.mappers.*;
import com.memmcol.hes.dto.*;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.infrastructure.dlms.ProfileMetadataProvider;
import com.memmcol.hes.infrastructure.persistence.*;
import com.memmcol.hes.model.ModelProfileMetadata;
import com.memmcol.hes.application.port.out.ProfileStatePort;
import com.memmcol.hes.nettyUtils.SessionManagerMultiVendor;
import com.memmcol.hes.service.DlmsUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Dedicated component for executing bulk DLMS profile reads across an entire date range [startDate, endDate]
 * in a single readRange call, without touching or mutating existing Quartz scheduled job state cursors.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BulkProfileRangeReader {

    private final MeterLockPort meterLockPort;
    private final DlmsReaderUtils dlmsReaderUtils;
    private final ProfileMetadataProvider metadataProvider;
    private final CapturePeriodPort capturePeriodPort;
    private final SessionManagerMultiVendor sessionManager;
    private final ProfileStatePort profileStatePort;

    // Mappers
    private final ProfileChannelOneMapper channelOneMapper;
    private final ProfileChannelOneHouseholdMapper channelOneHouseholdMapper;
    private final ProfileChannelTwoMapper channelTwoMapper;
    private final ProfileChannelTwoHouseholdMapper channelTwoHouseholdMapper;
    private final ProfileChannelThreeHouseholdMapper channelThreeHouseholdMapper;
    private final DailyBillingMapper dailyBillingMapper;
    private final BillingDataHouseholdMapper billingDataHouseholdMapper;
    private final BillingEnergyHouseholdMapper billingEnergyHouseholdMapper;
    private final MonthlyBillingMapper monthlyBillingMapper;
    private final EventLogMapper eventLogMapper;
    private final HouseholdRechargeTokenEventMapper rechargeTokenEventMapper;
    private final HouseholdManagementTokenEventMapper managementTokenEventMapper;
    private final HouseholdFraudEventMapper fraudEventMapper;
    private final HouseholdControlEventMapper controlEventMapper;

    // Persistence Adapters
    private final ProfileChannelOnePersistAdapter channelOnePersistAdapter;
    private final ProfileChannelOneHouseholdPersistAdapter channelOneHouseholdPersistAdapter;
    private final ProfileChannelTwoPersistenceAdapter channelTwoPersistAdapter;
    private final ProfileChannelTwoHouseholdPersistenceAdapter channelTwoHouseholdPersistAdapter;
    private final ProfileChannelThreeHouseholdPersistenceAdapter channelThreeHouseholdPersistAdapter;
    private final DailyBillingPersistenceAdapter dailyBillingPersistAdapter;
    private final DailyBillingDataHouseholdPersistenceAdapter dailyBillingDataHouseholdPersistAdapter;
    private final DailyBillingEnergyHouseholdPersistenceAdapter dailyBillingEnergyHouseholdPersistAdapter;
    private final MonthlyBillingPersistenceAdapter monthlyBillingPersistAdapter;
    private final MonthlyBillingDataHouseholdPersistenceAdapter monthlyBillingDataHouseholdPersistAdapter;
    private final MonthlyBillingEnergyHouseholdPersistenceAdapter monthlyBillingEnergyHouseholdPersistAdapter;
    private final EventLogPersistenceAdapter eventLogPersistAdapter;
    private final HouseholdRechargeTokenEventPersistenceAdapter rechargeTokenEventPersistAdapter;
    private final HouseholdManagementTokenEventPersistenceAdapter managementTokenEventPersistAdapter;
    private final HouseholdFraudEventPersistenceAdapter fraudEventPersistAdapter;
    private final HouseholdControlEventPersistenceAdapter controlEventPersistAdapter;

    public record BulkMeterReadSummary(
            int totalRowsPersisted,
            String txFrames,
            String rxFrames,
            String lastError,
            ProfileSyncResult syncResult
    ) {
        public BulkMeterReadSummary(int totalRowsPersisted, String txFrames, String rxFrames, String lastError) {
            this(totalRowsPersisted, txFrames, rxFrames, lastError, null);
        }
    }

    public BulkMeterReadSummary executeRangeRead(
            String jobName,
            String jobGroup,
            String jobClass,
            String obisCodes,
            String obisCodesHousehold,
            MeterDTO meter,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        String clazz = jobClass != null ? jobClass : "";
        String name = jobName != null ? jobName : "";

        String model = meter.getMeterModel();
        String serial = meter.getMeterNumber();
        boolean isMD = meter.isMD();

        // Determine profile read type
        ProfileType type = resolveProfileType(clazz, name);

        String hhObis = (obisCodesHousehold != null && !obisCodesHousehold.isBlank()) ? obisCodesHousehold : obisCodes;
        String rawObis = type.isHousehold() ? hhObis : obisCodes;
        String activeObis = resolveEffectiveObisCode(type, rawObis);

        // Resolve default date range if missing
        LocalDateTime fromDate = startDate != null ? startDate : LocalDateTime.now().minusDays(1);
        LocalDateTime toDate = endDate != null ? endDate : LocalDateTime.now();

        if (toDate.isBefore(fromDate)) {
            toDate = fromDate.plusDays(1);
        }

        log.info("Executing bulk single range read profileType={} meter={} model={} obis={} from={} to={}",
                type, serial, model, activeObis, fromDate, toDate);

        final LocalDateTime finalFromDate = fromDate;
        final LocalDateTime finalToDate = toDate;

        try {
            return meterLockPort.withExclusive(serial, () ->
                    readAndPersistRange(type, model, serial, activeObis, isMD, finalFromDate, finalToDate)
            );
        } catch (Exception ex) {
            log.error("Failed executing bulk range read for meter {}: {}", serial, ex.getMessage(), ex);
            return new BulkMeterReadSummary(0, "Range read requested: [" + finalFromDate + " to " + finalToDate + "] for OBIS " + activeObis, "FAILED: " + ex.getMessage(), ex.getMessage(), null);
        }
    }

    private String resolveEffectiveObisCode(ProfileType type, String providedObis) {
        if (providedObis != null && !providedObis.isBlank()) {
            return providedObis.trim();
        }
        return switch (type) {
            case CHANNEL_1, CHANNEL_1_HH -> "1.0.99.1.0.255";
            case CHANNEL_2, CHANNEL_2_HH -> "1.0.99.2.0.255";
            case CHANNEL_3_HH -> "1.0.99.3.0.255";
            case DAILY_BILLING, DAILY_BILLING_DATA_HH, DAILY_BILLING_ENERGY_HH -> "0.0.98.2.0.255";
            case MONTHLY_BILLING, MONTHLY_BILLING_DATA_HH, MONTHLY_BILLING_ENERGY_HH -> "0.0.98.1.0.255";
            case EVENT_LOG -> "0.0.99.98.0.255";
            case RECHARGE_TOKEN_EVENT -> "0.0.99.98.3.255";
            case MANAGEMENT_TOKEN_EVENT -> "0.0.99.98.5.255";
            case FRAUD_EVENT -> "0.0.99.98.1.255";
            case CONTROL_EVENT -> "0.0.99.98.2.255";
        };
    }

    /**
     * Reads the entire date range [fromDate, toDate] in a single DLMS readRange call.
     */
    private BulkMeterReadSummary readAndPersistRange(
            ProfileType type,
            String model,
            String serial,
            String obisCode,
            boolean isMD,
            LocalDateTime fromDate,
            LocalDateTime toDate
    ) {
        if (obisCode == null || obisCode.isBlank()) {
            log.warn("Cannot perform bulk range read: empty OBIS code for meter {}", serial);
            return new BulkMeterReadSummary(0, "Range read skipped: empty OBIS code", "ERROR: Empty OBIS code", "Empty OBIS code", null);
        }

        ProfileMetadataResult metadataResult = metadataProvider.resolve(serial, obisCode, model);

        // Meaningful TX diagnostic description replacing raw hex frames
        String txString = "Requested single range read [" + fromDate + " to " + toDate + "] for OBIS " + obisCode
                + " (profileType=" + type + ", model=" + model + ", isMD=" + isMD + ")";
        String rxString;
        int totalRowsPersisted = 0;
        String lastErrorMsg = null;
        ProfileSyncResult syncResult = null;

        try {
            List<ProfileRowGeneric> rows = dlmsReaderUtils.readRange(
                    model, serial, obisCode, metadataResult, fromDate, toDate, isMD
            );

            if (rows != null && !rows.isEmpty()) {
                syncResult = persistProfileRowsWithoutWatermark(type, rows, serial, model, obisCode, isMD, metadataResult);
                totalRowsPersisted = syncResult.getInsertedCount();
                rxString = "SUCCESS: Received " + rows.size() + " profile rows, inserted "
                        + syncResult.getInsertedCount() + ", duplicates " + syncResult.getDuplicateCount();
                log.info("Persisted {} bulk profile rows for meter {} [{}] range {} to {}: {}",
                        rows.size(), serial, type, fromDate, toDate, syncResult);
            } else {
                syncResult = buildEmptySyncResult(serial, obisCode);
                rxString = "EMPTY: 0 rows returned from meter DLMS buffer for range [" + fromDate + " to " + toDate + "]";
                log.info("No DLMS rows returned for meter {} [{}] range {} to {}",
                        serial, type, fromDate, toDate);
            }
        } catch (Exception ex) {
            lastErrorMsg = ex.getMessage();
            log.error("Error reading DLMS range for meter {} [{}] range {} to {}: {}. Attempting partial recovery...",
                    serial, type, fromDate, toDate, ex.getMessage(), ex);

            // Attempt to recover and persist partially received profile rows accumulated before failure/timeout
            try {
                List<ProfileRowGeneric> salvagedRows = dlmsReaderUtils.recoverPartial(serial, obisCode, model, metadataResult);
                if (salvagedRows != null && !salvagedRows.isEmpty()) {
                    syncResult = persistProfileRowsWithoutWatermark(type, salvagedRows, serial, model, obisCode, isMD, metadataResult);
                    totalRowsPersisted = syncResult.getInsertedCount();
                    rxString = "PARTIAL_RECOVERY: Recovered and persisted " + syncResult.getInsertedCount()
                            + " rows before failure (" + ex.getMessage() + ")";
                    log.info("Successfully recovered and persisted {} partial rows for meter {} [{}] range {} to {}",
                            salvagedRows.size(), serial, type, fromDate, toDate);
                } else {
                    rxString = "ERROR: " + ex.getMessage() + " (0 partial rows salvaged)";
                }
            } catch (Exception recEx) {
                log.error("Failed to recover partial rows for meter {}: {}", serial, recEx.getMessage(), recEx);
                rxString = "ERROR: " + ex.getMessage() + " (Partial recovery error: " + recEx.getMessage() + ")";
            }
        }

        return new BulkMeterReadSummary(totalRowsPersisted, txString, rxString, lastErrorMsg, syncResult);
    }

    private ProfileSyncResult buildEmptySyncResult(String meterSerial, String profileObis) {
        LocalDateTime previousLast = null;
        if (profileStatePort != null) {
            try {
                ProfileState st = profileStatePort.loadState(meterSerial, profileObis);
                if (st != null && st.lastTimestamp() != null) {
                    previousLast = st.lastTimestamp().value();
                }
            } catch (Exception e) {
                log.debug("Could not load ProfileState for meter {} obis {}", meterSerial, profileObis);
            }
        }
        return new ProfileSyncResult(0, 0, 0, previousLast, previousLast, previousLast, false);
    }

    /**
     * Persists rows directly into fact tables without mutating meter_profile_state,
     * and constructs an accurate ProfileSyncResult summary.
     */
    private ProfileSyncResult persistProfileRowsWithoutWatermark(
            ProfileType type,
            List<ProfileRowGeneric> rows,
            String serial,
            String model,
            String obisCode,
            boolean isMD,
            ProfileMetadataResult metadataResult
    ) throws Exception {
        int total = rows != null ? rows.size() : 0;
        int inserted = 0;
        LocalDateTime incomingMax = null;

        switch (type) {
            case CHANNEL_1 -> {
                List<ProfileChannelOneDTO> dtos = channelOneMapper.toDTO(rows, serial, model, isMD, metadataResult);
                channelOnePersistAdapter.createPartitionsIfMissing(dtos);
                inserted = channelOnePersistAdapter.persistReadingsByMonth(dtos);
                incomingMax = dtos.stream().map(ProfileChannelOneDTO::getEntryTimestamp).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
            }
            case CHANNEL_1_HH -> {
                List<ProfileChannelOneHouseholdDTO> dtos = channelOneHouseholdMapper.toDTO(rows, serial, model, isMD, metadataResult);
                channelOneHouseholdPersistAdapter.createPartitionsIfMissing(dtos);
                inserted = channelOneHouseholdPersistAdapter.persistReadingsByMonth(dtos);
                incomingMax = dtos.stream().map(ProfileChannelOneHouseholdDTO::getEntryTimestamp).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
            }
            case CHANNEL_2 -> {
                List<ProfileChannelTwoDTO> dtos = channelTwoMapper.toDTO(rows, serial, model, isMD, metadataResult);
                channelTwoPersistAdapter.createPartitionsIfMissing(dtos);
                inserted = channelTwoPersistAdapter.persistReadingsByMonth(dtos);
                incomingMax = dtos.stream().map(ProfileChannelTwoDTO::getEntryTimestamp).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
            }
            case CHANNEL_2_HH -> {
                List<ProfileChannelTwoHouseholdDTO> dtos = channelTwoHouseholdMapper.toDTO(rows, serial, model, isMD, metadataResult);
                channelTwoHouseholdPersistAdapter.createPartitionsIfMissing(dtos);
                inserted = channelTwoHouseholdPersistAdapter.persistReadingsByMonth(dtos);
                incomingMax = dtos.stream().map(ProfileChannelTwoHouseholdDTO::getEntryTimestamp).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
            }
            case CHANNEL_3_HH -> {
                List<ProfileChannelThreeHouseholdDTO> dtos = channelThreeHouseholdMapper.toDTO(rows, serial, model, isMD, metadataResult);
                channelThreeHouseholdPersistAdapter.createPartitionsIfMissing(dtos);
                inserted = channelThreeHouseholdPersistAdapter.persistReadingsByMonth(dtos);
                incomingMax = dtos.stream().map(ProfileChannelThreeHouseholdDTO::getEntryTimestamp).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
            }
            case DAILY_BILLING -> {
                List<DailyBillingProfileDTO> dtos = dailyBillingMapper.toDTO(rows, serial, model, isMD, metadataResult);
                dailyBillingPersistAdapter.createPartitionsIfMissing(dtos);
                inserted = dailyBillingPersistAdapter.persistReadingsByMonth(dtos);
                incomingMax = dtos.stream().map(DailyBillingProfileDTO::getEntryTimestamp).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
            }
            case DAILY_BILLING_DATA_HH -> {
                List<BillingDataHouseholdDTO> dtos = billingDataHouseholdMapper.toDTO(rows, serial, model, isMD, metadataResult);
                dailyBillingDataHouseholdPersistAdapter.createPartitionsIfMissing(dtos);
                inserted = dailyBillingDataHouseholdPersistAdapter.persistReadings(dtos).inserted();
                incomingMax = dtos.stream().map(BillingDataHouseholdDTO::getEntryTimestamp).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
            }
            case DAILY_BILLING_ENERGY_HH -> {
                List<BillingEnergyHouseholdDTO> dtos = billingEnergyHouseholdMapper.toDTO(rows, serial, model, isMD, metadataResult);
                inserted = dailyBillingEnergyHouseholdPersistAdapter.persistBatch(dtos).inserted();
                incomingMax = dtos.stream().map(BillingEnergyHouseholdDTO::getEntryTimestamp).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
            }
            case MONTHLY_BILLING -> {
                List<MonthlyBillingDTO> dtos = monthlyBillingMapper.toDTO(rows, serial, model, isMD, metadataResult);
                monthlyBillingPersistAdapter.createPartitionsIfMissing(dtos);
                inserted = monthlyBillingPersistAdapter.persistReadingsByMonth(dtos).inserted();
                incomingMax = dtos.stream().map(MonthlyBillingDTO::getEntryTimestamp).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
            }
            case MONTHLY_BILLING_DATA_HH -> {
                List<BillingDataHouseholdDTO> dtos = billingDataHouseholdMapper.toDTO(rows, serial, model, isMD, metadataResult);
                monthlyBillingDataHouseholdPersistAdapter.createPartitionsIfMissing(dtos);
                inserted = monthlyBillingDataHouseholdPersistAdapter.persistReadings(dtos).inserted();
                incomingMax = dtos.stream().map(BillingDataHouseholdDTO::getEntryTimestamp).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
            }
            case MONTHLY_BILLING_ENERGY_HH -> {
                List<BillingEnergyHouseholdDTO> dtos = billingEnergyHouseholdMapper.toDTO(rows, serial, model, isMD, metadataResult);
                monthlyBillingEnergyHouseholdPersistAdapter.createPartitionsIfMissing(dtos);
                inserted = monthlyBillingEnergyHouseholdPersistAdapter.persistReadings(dtos).inserted();
                incomingMax = dtos.stream().map(BillingEnergyHouseholdDTO::getEntryTimestamp).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
            }
            case EVENT_LOG -> {
                List<EventLogDTO> dtos = eventLogMapper.toDTOs(rows, serial, model);
                ProfileSyncResult res = eventLogPersistAdapter.saveBatch(serial, obisCode, dtos);
                return res;
            }
            case RECHARGE_TOKEN_EVENT -> {
                List<HouseholdRechargeTokenEventDTO> dtos = rechargeTokenEventMapper.toDTOs(rows, serial, model, obisCode);
                ProfileSyncResult res = rechargeTokenEventPersistAdapter.saveBatch(serial, obisCode, dtos);
                return res;
            }
            case MANAGEMENT_TOKEN_EVENT -> {
                List<HouseholdManagementTokenEventDTO> dtos = managementTokenEventMapper.toDTOs(rows, serial, model, obisCode);
                ProfileSyncResult res = managementTokenEventPersistAdapter.saveBatch(serial, obisCode, dtos);
                return res;
            }
            case FRAUD_EVENT -> {
                List<HouseholdFraudEventDTO> dtos = fraudEventMapper.toDTOs(rows, serial, model, obisCode);
                ProfileSyncResult res = fraudEventPersistAdapter.saveBatch(serial, obisCode, dtos);
                return res;
            }
            case CONTROL_EVENT -> {
                List<HouseholdControlEventDTO> dtos = controlEventMapper.toDTOs(rows, serial, model, obisCode);
                ProfileSyncResult res = controlEventPersistAdapter.saveBatch(serial, obisCode, dtos);
                return res;
            }
        }

        LocalDateTime previousLast = null;
        if (profileStatePort != null) {
            try {
                ProfileState st = profileStatePort.loadState(serial, obisCode);
                if (st != null && st.lastTimestamp() != null) {
                    previousLast = st.lastTimestamp().value();
                }
            } catch (Exception e) {
                log.debug("Could not read ProfileState for meter {} obis {}: {}", serial, obisCode, e.getMessage());
            }
        }

        if (incomingMax == null) {
            incomingMax = previousLast;
        }

        LocalDateTime advanceTo = previousLast == null
                ? incomingMax
                : (incomingMax != null && previousLast.isAfter(incomingMax) ? previousLast : incomingMax);

        boolean advanced = previousLast == null || (advanceTo != null && advanceTo.isAfter(previousLast));
        int duplicate = Math.max(0, total - inserted);

        return new ProfileSyncResult(total, inserted, duplicate, previousLast, incomingMax, advanceTo, advanced);
    }

    private enum ProfileType {
        CHANNEL_1(false),
        CHANNEL_1_HH(true),
        CHANNEL_2(false),
        CHANNEL_2_HH(true),
        CHANNEL_3_HH(true),
        DAILY_BILLING(false),
        DAILY_BILLING_DATA_HH(true),
        DAILY_BILLING_ENERGY_HH(true),
        MONTHLY_BILLING(false),
        MONTHLY_BILLING_DATA_HH(true),
        MONTHLY_BILLING_ENERGY_HH(true),
        EVENT_LOG(false),
        RECHARGE_TOKEN_EVENT(true),
        MANAGEMENT_TOKEN_EVENT(true),
        FRAUD_EVENT(true),
        CONTROL_EVENT(true);

        private final boolean household;

        ProfileType(boolean household) {
            this.household = household;
        }

        public boolean isHousehold() {
            return household;
        }
    }

    private ProfileType resolveProfileType(String clazz, String name) {
        String combined = (clazz + " " + name).toLowerCase();
        if (combined.contains("channel1jobhousehold") || combined.contains("channel1household") || combined.contains("channel1-hh")) return ProfileType.CHANNEL_1_HH;
        if (combined.contains("channel1")) return ProfileType.CHANNEL_1;
        if (combined.contains("channel2jobhousehold") || combined.contains("channel2household") || combined.contains("channel2-hh")) return ProfileType.CHANNEL_2_HH;
        if (combined.contains("channel2")) return ProfileType.CHANNEL_2;
        if (combined.contains("channel3jobhousehold") || combined.contains("channel3household") || combined.contains("channel3-hh")) return ProfileType.CHANNEL_3_HH;
        if (combined.contains("dailybillingdata")) return ProfileType.DAILY_BILLING_DATA_HH;
        if (combined.contains("dailybillingenergy")) return ProfileType.DAILY_BILLING_ENERGY_HH;
        if (combined.contains("dailybilling") || combined.contains("daily")) return ProfileType.DAILY_BILLING;
        if (combined.contains("monthlybillingdata")) return ProfileType.MONTHLY_BILLING_DATA_HH;
        if (combined.contains("monthlybillingenergy")) return ProfileType.MONTHLY_BILLING_ENERGY_HH;
        if (combined.contains("monthlybilling") || combined.contains("monthly")) return ProfileType.MONTHLY_BILLING;
        if (combined.contains("rechargetoken")) return ProfileType.RECHARGE_TOKEN_EVENT;
        if (combined.contains("managetoken") || combined.contains("managementtoken")) return ProfileType.MANAGEMENT_TOKEN_EVENT;
        if (combined.contains("fraud")) return ProfileType.FRAUD_EVENT;
        if (combined.contains("control")) return ProfileType.CONTROL_EVENT;
        if (combined.contains("event")) return ProfileType.EVENT_LOG;
        return ProfileType.CHANNEL_1;
    }
}
