package com.memmcol.hes.domain.profile;

import com.memmcol.hes.application.port.out.*;
import com.memmcol.hes.domain.events.HouseholdTokenEventObis;
import com.memmcol.hes.domain.profile.mappers.HouseholdManagementTokenEventMapper;
import com.memmcol.hes.domain.profile.mappers.HouseholdRechargeTokenEventMapper;
import com.memmcol.hes.dto.HouseholdManagementTokenEventDTO;
import com.memmcol.hes.dto.HouseholdRechargeTokenEventDTO;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.infrastructure.dlms.ProfileMetadataProvider;
import com.memmcol.hes.infrastructure.persistence.HouseholdManagementTokenEventPersistenceAdapter;
import com.memmcol.hes.infrastructure.persistence.HouseholdRechargeTokenEventPersistenceAdapter;
import com.memmcol.hes.repository.MeterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reads household recharge / management token event profiles and persists to dedicated tables
 * (not {@code event_log}; {@code event_type_id} resolved from profile OBIS like {@code event_log}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HouseholdTokenEventService {

    private final DlmsReaderUtils dlmsReaderUtils;
    private final ProfileMetricsPort metricsPort;
    private final ProfileStatePort statePort;
    private final ProfileMetadataProvider metadataProvider;
    private final HouseholdRechargeTokenEventPersistenceAdapter rechargePersistenceAdapter;
    private final HouseholdManagementTokenEventPersistenceAdapter managementPersistenceAdapter;
    private final HouseholdRechargeTokenEventMapper rechargeMapper;
    private final HouseholdManagementTokenEventMapper managementMapper;
    private final MeterRepository meterRepository;
    private final EventLogService eventLogService;

    public void readRechargeProfileAndSave(String model, String meterSerial, String profileObis, boolean isMD) {
        readProfileAndSave(model, meterSerial, profileObis, isMD, TokenKind.RECHARGE);
    }

    public void readManagementProfileAndSave(String model, String meterSerial, String profileObis, boolean isMD) {
        readProfileAndSave(model, meterSerial, profileObis, isMD, TokenKind.MANAGEMENT);
    }

    private enum TokenKind { RECHARGE, MANAGEMENT }

    private void readProfileAndSave(String model, String meterSerial, String profileObis, boolean isMD, TokenKind kind) {
        if (profileObis == null || profileObis.isBlank()) {
            log.error("Household token profile OBIS is blank; skipping meter={} model={} kind={}",
                    meterSerial, model, kind);
            metricsPort.recordFailure(meterSerial, "unknown", "missing_profile_obis");
            return;
        }

        if (!isExpectedObis(profileObis, kind)) {
            log.warn("OBIS {} does not match expected household {} token OBIS; proceeding with read",
                    profileObis, kind);
        }

        try {
            LocalDateTime seedFrom = HouseholdDayWindowIngestionSupport.resolveSeedTimestamp(
                    statePort,
                    meterSerial,
                    profileObis,
                    meterRepository.findMeterDetailsByMeterNumber(meterSerial)
                            .map(m -> m.getCreatedAt())
                            .orElse(null)
            );
            ProfileTimestamp cursor = new ProfileTimestamp(seedFrom);
            CapturePeriod cp = new CapturePeriod(1);
            LocalDateTime now = LocalDateTime.now();

            while (cursor.value().isBefore(now)) {
                LocalDateTime from = cursor.value();
                LocalDateTime to = from.plusDays(1);
                if (to.isAfter(now)) {
                    to = now;
                }

                long t0 = System.currentTimeMillis();
                boolean exceptionOccurred = false;

                final ProfileMetadataResult captureObjects;
                try {
                    captureObjects = metadataProvider.resolve(meterSerial, profileObis, model);
                } catch (Exception metaEx) {
                    log.error("Metadata resolve failed meter={} profile={} model={} cause={}",
                            meterSerial, profileObis, model, metaEx.getMessage(), metaEx);
                    metricsPort.recordFailure(meterSerial, profileObis, "metadata_resolve_failed");
                    return;
                }

                List<ProfileRowGeneric> rawRows;
                try {
                    rawRows = dlmsReaderUtils.readRange(model, meterSerial, profileObis, captureObjects, from, to, isMD);
                } catch (Exception e) {
                    log.warn("Range read failed; attempting recovery meter={} profile={} cause={}",
                            meterSerial, profileObis, e.getMessage());
                    exceptionOccurred = true;
                    rawRows = eventLogService.attemptRecovery(model, meterSerial, profileObis, captureObjects);
                }

                if ((rawRows == null || rawRows.isEmpty()) && exceptionOccurred) {
                    log.warn("Breaking (no rows + exception) meter={} profile={} from={} to={}",
                            meterSerial, profileObis, from, to);
                    return;
                }

                if (rawRows == null || rawRows.isEmpty()) {
                    log.warn("Empty profile response meter={} profile={} from={} to={}",
                            meterSerial, profileObis, from, to);
                    HouseholdDayWindowIngestionSupport.jumpAndPersistState(statePort, meterSerial, profileObis, to);
                    cursor = HouseholdDayWindowIngestionSupport.advanceInMemoryCursor(to);
                    continue;
                }

                ProfileSyncResult syncResult = mapAndPersistRows(kind, rawRows, meterSerial, model, profileObis);
                if (syncResult == null) {
                    log.warn("Mapped token event rows not persistable meter={} profile={} from={} to={} rawCount={}",
                            meterSerial, profileObis, from, to, rawRows.size());
                    HouseholdDayWindowIngestionSupport.jumpAndPersistState(statePort, meterSerial, profileObis, to);
                    cursor = HouseholdDayWindowIngestionSupport.advanceInMemoryCursor(to);
                    continue;
                }

                long t1 = System.currentTimeMillis() - t0;
                metricsPort.recordBatch(meterSerial, profileObis, syncResult.getInsertedCount(), t1);

                ProfileTimestamp resume = HouseholdDayWindowIngestionSupport.nextCursorAfterBatch(syncResult);
                if (resume == null || !resume.value().isAfter(from)) {
                    log.info("Persistence did not advance cursor meter={} profile={} to={}", meterSerial, profileObis, to);
                    HouseholdDayWindowIngestionSupport.jumpAndPersistState(statePort, meterSerial, profileObis, to);
                    cursor = HouseholdDayWindowIngestionSupport.advanceInMemoryCursor(to);
                } else {
                    cursor = resume;
                }

                if (cp.seconds() <= 0) {
                    log.warn("cp.seconds() <= 0 : {}", cp);
                    return;
                }
            }
        } catch (Exception ex) {
            log.error("Fatal exception while reading household token profile, meter={}, profile={}: {}",
                    meterSerial, profileObis, ex.getMessage(), ex);
            metricsPort.recordFailure(meterSerial, profileObis, "unhandled_exception");
        }
    }

    private ProfileSyncResult mapAndPersistRows(TokenKind kind,
                                                List<ProfileRowGeneric> rawRows,
                                                String meterSerial,
                                                String model,
                                                String profileObis) {
        return switch (kind) {
            case RECHARGE -> {
                List<HouseholdRechargeTokenEventDTO> dtos =
                        rechargeMapper.toDTOs(rawRows, meterSerial, model, profileObis);
                if (HouseholdDayWindowIngestionSupport.shouldSkipUnmappableBatch(
                        dtos, dto -> dto.getEventTime() != null && dto.getEventCode() != null)) {
                    yield null;
                }
                yield rechargePersistenceAdapter.saveBatch(meterSerial, profileObis, dtos);
            }
            case MANAGEMENT -> {
                List<HouseholdManagementTokenEventDTO> dtos =
                        managementMapper.toDTOs(rawRows, meterSerial, model, profileObis);
                if (HouseholdDayWindowIngestionSupport.shouldSkipUnmappableBatch(
                        dtos, dto -> dto.getEventTime() != null && dto.getEventCode() != null)) {
                    yield null;
                }
                yield managementPersistenceAdapter.saveBatch(meterSerial, profileObis, dtos);
            }
        };
    }

    private static boolean isExpectedObis(String profileObis, TokenKind kind) {
        return switch (kind) {
            case RECHARGE -> HouseholdTokenEventObis.isRecharge(profileObis);
            case MANAGEMENT -> HouseholdTokenEventObis.isManagement(profileObis);
        };
    }
}
