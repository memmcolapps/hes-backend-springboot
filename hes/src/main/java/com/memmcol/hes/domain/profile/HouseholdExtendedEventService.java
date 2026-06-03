package com.memmcol.hes.domain.profile;

import com.memmcol.hes.application.port.out.*;
import com.memmcol.hes.domain.events.HouseholdExtendedEventObis;
import com.memmcol.hes.domain.profile.mappers.HouseholdControlEventMapper;
import com.memmcol.hes.domain.profile.mappers.HouseholdFraudEventMapper;
import com.memmcol.hes.dto.HouseholdControlEventDTO;
import com.memmcol.hes.dto.HouseholdFraudEventDTO;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.infrastructure.dlms.ProfileMetadataProvider;
import com.memmcol.hes.infrastructure.persistence.HouseholdControlEventPersistenceAdapter;
import com.memmcol.hes.infrastructure.persistence.HouseholdFraudEventPersistenceAdapter;
import com.memmcol.hes.repository.MeterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reads household fraud / control event profiles (same profile OBIS as MD, extended capture columns)
 * into {@code household_fraud_event} and {@code household_control_event}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HouseholdExtendedEventService {

    private final DlmsReaderUtils dlmsReaderUtils;
    private final ProfileMetricsPort metricsPort;
    private final ProfileStatePort statePort;
    private final ProfileMetadataProvider metadataProvider;
    private final HouseholdFraudEventPersistenceAdapter fraudPersistenceAdapter;
    private final HouseholdControlEventPersistenceAdapter controlPersistenceAdapter;
    private final HouseholdFraudEventMapper fraudMapper;
    private final HouseholdControlEventMapper controlMapper;
    private final MeterRepository meterRepository;
    private final EventLogService eventLogService;

    public void readFraudProfileAndSave(String model, String meterSerial, String profileObis, boolean isMD) {
        readProfileAndSave(model, meterSerial, profileObis, isMD, EventKind.FRAUD);
    }

    public void readControlProfileAndSave(String model, String meterSerial, String profileObis, boolean isMD) {
        readProfileAndSave(model, meterSerial, profileObis, isMD, EventKind.CONTROL);
    }

    private enum EventKind { FRAUD, CONTROL }

    private void readProfileAndSave(String model, String meterSerial, String profileObis, boolean isMD, EventKind kind) {
        if (profileObis == null || profileObis.isBlank()) {
            log.error("Household extended event profile OBIS is blank; skipping meter={} model={} kind={}",
                    meterSerial, model, kind);
            metricsPort.recordFailure(meterSerial, "unknown", "missing_profile_obis");
            return;
        }

        if (!isExpectedObis(profileObis, kind)) {
            log.warn("OBIS {} does not match expected household {} event OBIS; proceeding with read",
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
                    log.warn("Mapped event rows not persistable meter={} profile={} from={} to={} rawCount={}",
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
            log.error("Fatal exception while reading household extended event profile, meter={}, profile={}: {}",
                    meterSerial, profileObis, ex.getMessage(), ex);
            metricsPort.recordFailure(meterSerial, profileObis, "unhandled_exception");
        }
    }

    /**
     * @return null when mapping yields no persistable rows (caller advances cursor past the window)
     */
    private ProfileSyncResult mapAndPersistRows(EventKind kind,
                                                List<ProfileRowGeneric> rawRows,
                                                String meterSerial,
                                                String model,
                                                String profileObis) {
        return switch (kind) {
            case FRAUD -> {
                List<HouseholdFraudEventDTO> dtos =
                        fraudMapper.toDTOs(rawRows, meterSerial, model, profileObis);
                if (HouseholdDayWindowIngestionSupport.shouldSkipUnmappableBatch(
                        dtos, dto -> dto.getEventTime() != null && dto.getEventCode() != null)) {
                    yield null;
                }
                yield fraudPersistenceAdapter.saveBatch(meterSerial, profileObis, dtos);
            }
            case CONTROL -> {
                List<HouseholdControlEventDTO> dtos =
                        controlMapper.toDTOs(rawRows, meterSerial, model, profileObis);
                if (HouseholdDayWindowIngestionSupport.shouldSkipUnmappableBatch(
                        dtos, dto -> dto.getEventTime() != null && dto.getEventCode() != null)) {
                    yield null;
                }
                yield controlPersistenceAdapter.saveBatch(meterSerial, profileObis, dtos);
            }
        };
    }

    private static boolean isExpectedObis(String profileObis, EventKind kind) {
        return switch (kind) {
            case FRAUD -> HouseholdExtendedEventObis.isFraud(profileObis);
            case CONTROL -> HouseholdExtendedEventObis.isControl(profileObis);
        };
    }
}
