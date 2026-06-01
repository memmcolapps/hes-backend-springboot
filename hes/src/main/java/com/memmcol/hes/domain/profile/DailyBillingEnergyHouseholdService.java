package com.memmcol.hes.domain.profile;

import com.memmcol.hes.application.port.out.ProfileMetricsPort;
import com.memmcol.hes.application.port.out.ProfileReadException;
import com.memmcol.hes.application.port.out.ProfileStatePort;
import com.memmcol.hes.domain.profile.mappers.BillingEnergyHouseholdMapper;
import com.memmcol.hes.dto.BillingEnergyHouseholdDTO;
import com.memmcol.hes.infrastructure.dlms.DlmsReadErrors;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.infrastructure.dlms.ProfileMetadataProvider;
import com.memmcol.hes.infrastructure.persistence.DailyBillingEnergyHouseholdPersistenceAdapter;
import com.memmcol.hes.repository.MeterRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Daily billing energy (household) ingestion — structurally aligned with
 * {@link DailyBillingDataHouseholdService}, which is the known-good reference pipeline.
 */
@AllArgsConstructor
@Slf4j
@Service
public class DailyBillingEnergyHouseholdService {
    private final DlmsReaderUtils dlmsReaderUtils;
    private final ProfileMetricsPort metricsPort;
    private final ProfileStatePort statePort;
    private final ProfileMetadataProvider metadataProvider;
    private final BillingEnergyHouseholdMapper mapper;
    private final DailyBillingEnergyHouseholdPersistenceAdapter persistenceAdapter;
    private final MeterRepository meterRepository;

    public void readProfileAndSave(String model, String meterSerial, String profileObis, boolean isMD) {
        try {
            final String safeObis = (profileObis == null || profileObis.isBlank()) ? "unknown" : profileObis;
            if (profileObis == null || profileObis.isBlank()) {
                metricsPort.recordFailure(meterSerial, safeObis, "missing_profile_obis");
                return;
            }

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
            boolean metadataRefreshedFromMeter = false;

            while (cursor.value().isBefore(now)) {
                LocalDateTime from = cursor.value();
                LocalDateTime to = from.plusDays(1);
                if (to.isAfter(now)) {
                    to = now;
                }

                boolean exceptionOccurred = false;
                ProfileMetadataResult metadataResult;
                try {
                    metadataResult = metadataProvider.resolve(meterSerial, profileObis, model);
                } catch (Exception metaEx) {
                    metricsPort.recordFailure(meterSerial, safeObis, "metadata_resolve_failed");
                    return;
                }

                List<ProfileRowGeneric> rawRows;
                try {
                    rawRows = dlmsReaderUtils.readRange(model, meterSerial, profileObis, metadataResult, from, to, isMD);
                } catch (Exception e) {
                    if (!metadataRefreshedFromMeter && DlmsReadErrors.isCaptureColumnMismatch(e)) {
                        metadataRefreshedFromMeter = true;
                        log.warn(
                                "Capture column mismatch meter={} profile={} model={}; refreshing metadata from meter",
                                meterSerial, profileObis, model);
                        metricsPort.recordFailure(meterSerial, profileObis, "metadata_refresh_column_mismatch");
                        try {
                            metadataResult = metadataProvider.refreshFromMeter(meterSerial, profileObis, model);
                            rawRows = dlmsReaderUtils.readRange(
                                    model, meterSerial, profileObis, metadataResult, from, to, isMD);
                        } catch (Exception retryEx) {
                            log.error("Range read failed after metadata refresh meter={} profile={} cause={}",
                                    meterSerial, profileObis, retryEx.getMessage());
                            exceptionOccurred = true;
                            rawRows = attemptRecovery(model, meterSerial, profileObis, metadataResult);
                        }
                    } else {
                        exceptionOccurred = true;
                        rawRows = attemptRecovery(model, meterSerial, profileObis, metadataResult);
                    }
                }

                if ((rawRows == null || rawRows.isEmpty()) && exceptionOccurred) {
                    return;
                }
                if (rawRows == null || rawRows.isEmpty()) {
                    log.warn("Empty profile response meter={} profile={} from={} to={}",
                            meterSerial, profileObis, from, to);
                    cursor = HouseholdDayWindowIngestionSupport.advanceInMemoryCursor(to);
                    continue;
                }

                List<BillingEnergyHouseholdDTO> dtos = mapper.toDTO(rawRows, meterSerial, model, isMD, metadataResult);
                if (HouseholdDayWindowIngestionSupport.shouldSkipUnmappableBatch(
                        dtos, dto -> dto.getEntryTimestamp() != null)) {
                    log.warn("Mapped rows lack entry_timestamp meter={} profile={} from={} to={} rawCount={}",
                            meterSerial, profileObis, from, to, rawRows.size());
                    cursor = HouseholdDayWindowIngestionSupport.advanceInMemoryCursor(to);
                    continue;
                }

                persistenceAdapter.createPartitionsIfMissing(dtos);
                ProfileSyncResult syncResult = persistenceAdapter.saveBatchAndAdvanceCursor(
                        meterSerial, profileObis, dtos, cp);
                metricsPort.recordBatch(meterSerial, profileObis, syncResult.getInsertedCount(), 0);

                ProfileTimestamp resume = HouseholdDayWindowIngestionSupport.nextCursorAfterBatch(syncResult);
                if (resume == null) {
                    log.warn("No meter-derived advanceTo after persist meter={} profile={}", meterSerial, profileObis);
                    break;
                }
                cursor = resume;

                if (cp.seconds() <= 0) {
                    log.warn("cp.seconds() <= 0 : {}", cp);
                    return;
                }
            }
        } catch (Exception ex) {
            String safeObis = (profileObis == null || profileObis.isBlank()) ? "unknown" : profileObis;
            metricsPort.recordFailure(meterSerial, safeObis, "unhandled_exception");
        }
    }

    private List<ProfileRowGeneric> attemptRecovery(String model, String serial, String profileObis,
                                                    ProfileMetadataResult metadataResult) {
        try {
            return dlmsReaderUtils.recoverPartial(serial, profileObis, model, metadataResult);
        } catch (ProfileReadException e) {
            metricsPort.recordFailure(serial, profileObis, "recovery_failed");
            return List.of();
        }
    }
}
