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
import com.memmcol.hes.nettyUtils.SessionManagerMultiVendor;
import com.memmcol.hes.service.DlmsUtils;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXDateTime;
import gurux.dlms.objects.GXDLMSProfileGeneric;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Dedicated component for executing bulk DLMS profile reads across an explicit date range [startDate, endDate]
 * without touching or mutating existing Quartz scheduled job state cursors (meter_profile_state).
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
            String lastError
    ) {}

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

        log.info("Executing bulk range read profileType={} meter={} model={} obis={} from={} to={}",
                type, serial, model, activeObis, fromDate, toDate);

        final LocalDateTime finalFromDate = fromDate;
        final LocalDateTime finalToDate = toDate;

        try {
            return meterLockPort.withExclusive(serial, () ->
                    readAndPersistRangeInChunks(type, model, serial, activeObis, isMD, finalFromDate, finalToDate)
            );
        } catch (Exception ex) {
            log.error("Failed executing bulk range read for meter {}: {}", serial, ex.getMessage(), ex);
            return new BulkMeterReadSummary(0, "", "[ERROR] " + ex.getMessage(), ex.getMessage());
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

    private BulkMeterReadSummary readAndPersistRangeInChunks(
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
            return new BulkMeterReadSummary(0, "", "[ERROR] Empty OBIS code", "Empty OBIS code");
        }

        ProfileMetadataResult metadataResult = metadataProvider.resolve(serial, obisCode, model);

        List<String> allTxFrames = new ArrayList<>();
        List<String> allRxFrames = new ArrayList<>();
        int totalRowsPersisted = 0;
        Exception lastException = null;

        GXDLMSClient client = null;
        try {
            client = sessionManager.getOrCreateClient(serial);
        } catch (Exception e) {
            log.warn("Could not get DLMS client for TX frame preview for meter {}: {}", serial, e.getMessage());
        }

        LocalDateTime cursor = fromDate;
        while (cursor.isBefore(toDate)) {
            LocalDateTime chunkEnd = type == ProfileType.MONTHLY_BILLING || type == ProfileType.MONTHLY_BILLING_DATA_HH || type == ProfileType.MONTHLY_BILLING_ENERGY_HH
                    ? cursor.plusMonths(1)
                    : cursor.plusDays(1);

            if (chunkEnd.isAfter(toDate)) {
                chunkEnd = toDate;
            }

            // Capture TX frame
            String chunkTx = captureTxFrames(client, obisCode, metadataResult, cursor, chunkEnd);
            allTxFrames.add("[" + cursor + " to " + chunkEnd + "] " + chunkTx);

            try {
                List<ProfileRowGeneric> rows = dlmsReaderUtils.readRange(
                        model, serial, obisCode, metadataResult, cursor, chunkEnd, isMD
                );

                if (rows != null && !rows.isEmpty()) {
                    persistProfileRowsWithoutWatermark(type, rows, serial, model, obisCode, isMD, metadataResult);
                    totalRowsPersisted += rows.size();
                    allRxFrames.add("[" + cursor + " to " + chunkEnd + "] SUCCESS: Persisted " + rows.size() + " rows");
                    log.info("Persisted {} bulk profile rows for meter {} [{}] range {} to {}",
                            rows.size(), serial, type, cursor, chunkEnd);
                } else {
                    allRxFrames.add("[" + cursor + " to " + chunkEnd + "] EMPTY: 0 rows returned from meter buffer");
                    log.info("No DLMS rows returned for meter {} [{}] range {} to {}",
                            serial, type, cursor, chunkEnd);
                }
            } catch (Exception ex) {
                lastException = ex;
                allRxFrames.add("[" + cursor + " to " + chunkEnd + "] ERROR: " + ex.getMessage());
                log.error("Error reading DLMS range for meter {} [{}] range {} to {}: {}",
                        serial, type, cursor, chunkEnd, ex.getMessage(), ex);
            }

            cursor = chunkEnd;
        }

        String txString = String.join("\n", allTxFrames);
        String rxString = String.join("\n", allRxFrames);
        String lastErrorMsg = lastException != null ? lastException.getMessage() : null;

        return new BulkMeterReadSummary(totalRowsPersisted, txString, rxString, lastErrorMsg);
    }

    private String captureTxFrames(
            GXDLMSClient client,
            String obisCode,
            ProfileMetadataResult metadataResult,
            LocalDateTime from,
            LocalDateTime to
    ) {
        if (client == null) {
            return "N/A (Client unavailable)";
        }
        try {
            GXDLMSProfileGeneric profile = new GXDLMSProfileGeneric();
            profile.setLogicalName(obisCode);
            if (metadataResult != null && metadataResult.getMetadataList() != null) {
                DlmsUtils.populateCaptureObjects(profile, metadataResult.getMetadataList());
            }

            GXDateTime gxFrom = new GXDateTime(Date.from(from.atZone(ZoneId.systemDefault()).toInstant()));
            GXDateTime gxTo = new GXDateTime(Date.from(to.atZone(ZoneId.systemDefault()).toInstant()));

            byte[][] frames = client.readRowsByRange(profile, gxFrom, gxTo);
            if (frames == null || frames.length == 0) {
                return "EMPTY_TX_FRAMES";
            }
            List<String> hexList = new ArrayList<>();
            for (byte[] f : frames) {
                hexList.add(toHex(f));
            }
            return String.join(" | ", hexList);
        } catch (Exception e) {
            return "ERR_GENERATING_TX: " + e.getMessage();
        }
    }

    private String toHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    /**
     * Persists rows directly into fact tables without mutating meter_profile_state.
     */
    private void persistProfileRowsWithoutWatermark(
            ProfileType type,
            List<ProfileRowGeneric> rows,
            String serial,
            String model,
            String obisCode,
            boolean isMD,
            ProfileMetadataResult metadataResult
    ) throws Exception {
        switch (type) {
            case CHANNEL_1 -> {
                List<ProfileChannelOneDTO> dtos = channelOneMapper.toDTO(rows, serial, model, isMD, metadataResult);
                channelOnePersistAdapter.createPartitionsIfMissing(dtos);
                channelOnePersistAdapter.persistReadingsByMonth(dtos);
            }
            case CHANNEL_1_HH -> {
                List<ProfileChannelOneHouseholdDTO> dtos = channelOneHouseholdMapper.toDTO(rows, serial, model, isMD, metadataResult);
                channelOneHouseholdPersistAdapter.createPartitionsIfMissing(dtos);
                channelOneHouseholdPersistAdapter.persistReadingsByMonth(dtos);
            }
            case CHANNEL_2 -> {
                List<ProfileChannelTwoDTO> dtos = channelTwoMapper.toDTO(rows, serial, model, isMD, metadataResult);
                channelTwoPersistAdapter.createPartitionsIfMissing(dtos);
                channelTwoPersistAdapter.persistReadingsByMonth(dtos);
            }
            case CHANNEL_2_HH -> {
                List<ProfileChannelTwoHouseholdDTO> dtos = channelTwoHouseholdMapper.toDTO(rows, serial, model, isMD, metadataResult);
                channelTwoHouseholdPersistAdapter.createPartitionsIfMissing(dtos);
                channelTwoHouseholdPersistAdapter.persistReadingsByMonth(dtos);
            }
            case CHANNEL_3_HH -> {
                List<ProfileChannelThreeHouseholdDTO> dtos = channelThreeHouseholdMapper.toDTO(rows, serial, model, isMD, metadataResult);
                channelThreeHouseholdPersistAdapter.createPartitionsIfMissing(dtos);
                channelThreeHouseholdPersistAdapter.persistReadingsByMonth(dtos);
            }
            case DAILY_BILLING -> {
                List<DailyBillingProfileDTO> dtos = dailyBillingMapper.toDTO(rows, serial, model, isMD, metadataResult);
                dailyBillingPersistAdapter.createPartitionsIfMissing(dtos);
                dailyBillingPersistAdapter.persistReadingsByMonth(dtos);
            }
            case DAILY_BILLING_DATA_HH -> {
                List<BillingDataHouseholdDTO> dtos = billingDataHouseholdMapper.toDTO(rows, serial, model, isMD, metadataResult);
                dailyBillingDataHouseholdPersistAdapter.createPartitionsIfMissing(dtos);
                dailyBillingDataHouseholdPersistAdapter.persistReadings(dtos);
            }
            case DAILY_BILLING_ENERGY_HH -> {
                List<BillingEnergyHouseholdDTO> dtos = billingEnergyHouseholdMapper.toDTO(rows, serial, model, isMD, metadataResult);
//                dailyBillingEnergyHouseholdPersistAdapter.createPartitionsIfMissing(dtos);
//                dailyBillingEnergyHouseholdPersistAdapter.persistReadings(dtos);
                dailyBillingEnergyHouseholdPersistAdapter.persistBatch(dtos);
            }
            case MONTHLY_BILLING -> {
                List<MonthlyBillingDTO> dtos = monthlyBillingMapper.toDTO(rows, serial, model, isMD, metadataResult);
                monthlyBillingPersistAdapter.createPartitionsIfMissing(dtos);
                monthlyBillingPersistAdapter.persistReadingsByMonth(dtos);
            }
            case MONTHLY_BILLING_DATA_HH -> {
                List<BillingDataHouseholdDTO> dtos = billingDataHouseholdMapper.toDTO(rows, serial, model, isMD, metadataResult);
                monthlyBillingDataHouseholdPersistAdapter.createPartitionsIfMissing(dtos);
                monthlyBillingDataHouseholdPersistAdapter.persistReadings(dtos);
            }
            case MONTHLY_BILLING_ENERGY_HH -> {
                List<BillingEnergyHouseholdDTO> dtos = billingEnergyHouseholdMapper.toDTO(rows, serial, model, isMD, metadataResult);
                monthlyBillingEnergyHouseholdPersistAdapter.createPartitionsIfMissing(dtos);
                monthlyBillingEnergyHouseholdPersistAdapter.persistReadings(dtos);
            }
            case EVENT_LOG -> {
                List<EventLogDTO> dtos = eventLogMapper.toDTOs(rows, serial, model);
                eventLogPersistAdapter.saveBatch(serial, obisCode, dtos);
            }
            case RECHARGE_TOKEN_EVENT -> {
                List<HouseholdRechargeTokenEventDTO> dtos = rechargeTokenEventMapper.toDTOs(rows, serial, model, obisCode);
                rechargeTokenEventPersistAdapter.saveBatch(serial, obisCode, dtos);
            }
            case MANAGEMENT_TOKEN_EVENT -> {
                List<HouseholdManagementTokenEventDTO> dtos = managementTokenEventMapper.toDTOs(rows, serial, model, obisCode);
                managementTokenEventPersistAdapter.saveBatch(serial, obisCode, dtos);
            }
            case FRAUD_EVENT -> {
                List<HouseholdFraudEventDTO> dtos = fraudEventMapper.toDTOs(rows, serial, model, obisCode);
                fraudEventPersistAdapter.saveBatch(serial, obisCode, dtos);
            }
            case CONTROL_EVENT -> {
                List<HouseholdControlEventDTO> dtos = controlEventMapper.toDTOs(rows, serial, model, obisCode);
                controlEventPersistAdapter.saveBatch(serial, obisCode, dtos);
            }
        }
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
