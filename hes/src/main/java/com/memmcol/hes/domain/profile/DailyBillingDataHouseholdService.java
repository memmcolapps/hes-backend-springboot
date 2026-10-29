package com.memmcol.hes.domain.profile;

import com.memmcol.hes.application.port.out.ProfileMetricsPort;
import com.memmcol.hes.application.port.out.ProfileReadException;
import com.memmcol.hes.application.port.out.ProfileStatePort;
import com.memmcol.hes.domain.profile.mappers.BillingDataHouseholdMapper;
import com.memmcol.hes.dto.BillingDataHouseholdDTO;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.infrastructure.dlms.ProfileMetadataProvider;
import com.memmcol.hes.infrastructure.persistence.DailyBillingDataHouseholdPersistenceAdapter;
import com.memmcol.hes.repository.MeterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
@Service
public class DailyBillingDataHouseholdService {
    private final DlmsReaderUtils dlmsReaderUtils;
    private final ProfileMetricsPort metricsPort;
    private final ProfileStatePort statePort;
    private final ProfileMetadataProvider metadataProvider;
    private final BillingDataHouseholdMapper mapper;
    private final DailyBillingDataHouseholdPersistenceAdapter persistenceAdapter;
    private final MeterRepository meterRepository;

    private int maxRangesPerRun = 7;

    @Value("${hes.profile.catchup.max-ranges-per-run:7}")
    public void setMaxRangesPerRun(int value) {
        this.maxRangesPerRun = Math.max(1, value);
    }

    public void readProfileAndSave(String model, String meterSerial, String profileObis, boolean isMD) {
        readProfileAndSave(model, meterSerial, profileObis, isMD, null, null);
    }

    public void readProfileAndSave(String model, String meterSerial, String profileObis, boolean isMD, LocalDateTime rangeFrom, LocalDateTime rangeTo) {
        try {
            final String safeObis = (profileObis == null || profileObis.isBlank()) ? "unknown" : profileObis;
            if (profileObis == null || profileObis.isBlank()) {
                metricsPort.recordFailure(meterSerial, safeObis, "missing_profile_obis");
                return;
            }

            LocalDateTime seedFrom = rangeFrom;
            if (seedFrom == null) {
                seedFrom = HouseholdDayWindowIngestionSupport.resolveSeedTimestamp(
                        statePort,
                        meterSerial,
                        profileObis,
                        meterRepository.findMeterDetailsByMeterNumber(meterSerial)
                                .map(m -> m.getCreatedAt())
                                .orElse(null)
                );
            }

            ProfileTimestamp cursor = new ProfileTimestamp(seedFrom);
            CapturePeriod cp = new CapturePeriod(1);
            LocalDateTime now = rangeTo != null ? rangeTo : LocalDateTime.now();

            int rangesProcessed = 0;
            while (cursor.value().isBefore(now) && rangesProcessed < maxRangesPerRun) {
                rangesProcessed++;
                LocalDateTime from = cursor.value();
                LocalDateTime to = from.plusDays(1);
                if (to.isAfter(now)) to = now;

                boolean exceptionOccurred = false;
                final ProfileMetadataResult metadataResult;
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
                    exceptionOccurred = true;
                    rawRows = attemptRecovery(model, meterSerial, profileObis, metadataResult);
                }

                if ((rawRows == null || rawRows.isEmpty()) && exceptionOccurred) return;
                if (rawRows == null || rawRows.isEmpty()) {
                    log.warn("Empty profile response meter={} profile={} from={} to={}",
                            meterSerial, profileObis, from, to);
                    cursor = HouseholdDayWindowIngestionSupport.advanceInMemoryCursor(to);
                    continue;
                }

                List<BillingDataHouseholdDTO> dtos = mapper.toDTO(rawRows, meterSerial, model, isMD, metadataResult);
                if (HouseholdDayWindowIngestionSupport.shouldSkipUnmappableBatch(
                        dtos, dto -> dto.getEntryTimestamp() != null)) {
                    log.warn("Mapped rows lack entry_timestamp meter={} profile={} from={} to={} rawCount={}",
                            meterSerial, profileObis, from, to, rawRows.size());
                    cursor = HouseholdDayWindowIngestionSupport.advanceInMemoryCursor(to);
                    continue;
                }
                persistenceAdapter.createPartitionsIfMissing(dtos);
                ProfileSyncResult syncResult = persistenceAdapter.saveBatchAndAdvanceCursor(meterSerial, profileObis, dtos, cp);
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

            if (cursor.value().isBefore(now)) {
                log.info("Catch-up capped at {} range(s) this run. meter={} profile={} resumeFrom={}",
                        maxRangesPerRun, meterSerial, profileObis, cursor.value());
            }
        } catch (Exception ex) {
            String safeObis = (profileObis == null || profileObis.isBlank()) ? "unknown" : profileObis;
            metricsPort.recordFailure(meterSerial, safeObis, "unhandled_exception");
        }
    }

    private List<ProfileRowGeneric> attemptRecovery(String model, String serial, String profileObis, ProfileMetadataResult metadataResult) {
        try {
            return dlmsReaderUtils.recoverPartial(serial, profileObis, model, metadataResult);
        } catch (ProfileReadException e) {
            metricsPort.recordFailure(serial, profileObis, "recovery_failed");
            return List.of();
        }
    }
}

