package com.memmcol.hes.domain.profile;

import com.memmcol.hes.application.port.out.ProfileMetricsPort;
import com.memmcol.hes.application.port.out.ProfileReadException;
import com.memmcol.hes.application.port.out.ProfileStatePort;
import com.memmcol.hes.config.BillingDeterminantsProperties;
import com.memmcol.hes.domain.profile.mappers.BillingDataHouseholdMapper;
import com.memmcol.hes.dto.BillingDataHouseholdDTO;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.infrastructure.dlms.ProfileMetadataProvider;
import com.memmcol.hes.infrastructure.persistence.MonthlyBillingDataHouseholdPersistenceAdapter;
import com.memmcol.hes.repository.MeterRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@AllArgsConstructor
@Slf4j
@Service
public class MonthlyBillingDataHouseholdService {
    private final DlmsReaderUtils dlmsReaderUtils;
    private final ProfileMetricsPort metricsPort;
    private final ProfileStatePort statePort;
    private final ProfileMetadataProvider metadataProvider;
    private final BillingDataHouseholdMapper mapper;
    private final MonthlyBillingDataHouseholdPersistenceAdapter persistenceAdapter;
    private final MeterRepository meterRepository;
    private final BillingDeterminantsProperties billingProperties;
    private final BillingCycleGracePeriodPolicy gracePeriodPolicy;

    public void readProfileAndSave(String model, String meterSerial, String profileObis, boolean isMD) {
        try {
            final String safeObis = (profileObis == null || profileObis.isBlank()) ? "unknown" : profileObis;

            if (profileObis == null || profileObis.isBlank()) {
                metricsPort.recordFailure(meterSerial, safeObis, "missing_profile_obis");
                return;
            }

            log.info("MonthlyBillingDataHousehold read start. meter={} model={} obis={} md={}",
                    meterSerial, model, safeObis, isMD);

            LocalDateTime meterCreatedAt = meterRepository.findMeterDetailsByMeterNumber(meterSerial)
                    .map(m -> m.getCreatedAt())
                    .orElse(null);

            LocalDateTime seedFrom = BillingDeterminantsIngestionSupport.resolveSeedTimestamp(
                    statePort, meterSerial, profileObis, meterCreatedAt);
            seedFrom = BillingDeterminantsIngestionSupport.effectiveSeedFrom(seedFrom, gracePeriodPolicy);

            ProfileTimestamp cursor = new ProfileTimestamp(seedFrom);
            CapturePeriod cp = new CapturePeriod(1);
            LocalDateTime now = LocalDateTime.now();
            int readWindowDays = billingProperties.getReadWindowDays();

            while (cursor.value().isBefore(now)) {
                LocalDateTime from = cursor.value();
                LocalDateTime to = BillingDeterminantsIngestionSupport.computeWindowEnd(from, now, readWindowDays);

                log.info("MonthlyBillingDataHousehold range meter={} obis={} from={} to={}",
                        meterSerial, safeObis, from, to);

                final ProfileMetadataResult metadataResult;
                try {
                    metadataResult = metadataProvider.resolve(meterSerial, profileObis, model);
                } catch (Exception metaEx) {
                    metricsPort.recordFailure(meterSerial, safeObis, "metadata_resolve_failed");
                    return;
                }

                boolean exceptionOccurred = false;
                List<ProfileRowGeneric> rawRows;

                try {
                    rawRows = dlmsReaderUtils.readRange(
                            model, meterSerial, profileObis, metadataResult, from, to, isMD
                    );
                } catch (Exception e) {
                    exceptionOccurred = true;
                    rawRows = attemptRecovery(model, meterSerial, profileObis, metadataResult);
                }

                if (BillingDeterminantsIngestionSupport.shouldBreakOnEmptyWithException(exceptionOccurred, rawRows)) {
                    return;
                }

                if (BillingDeterminantsIngestionSupport.isEmptyWindow(rawRows)) {
                    log.warn("Empty profile response meter={} profile={} from={} to={}",
                            meterSerial, profileObis, from, to);
                    cursor = BillingDeterminantsIngestionSupport.advanceInMemoryCursor(to);
                    continue;
                }

                List<BillingDataHouseholdDTO> dtos =
                        mapper.toDTO(rawRows, meterSerial, model, isMD, metadataResult);

                persistenceAdapter.createPartitionsIfMissing(dtos);

                ProfileSyncResult syncResult =
                        persistenceAdapter.saveBatchAndAdvanceCursor(
                                meterSerial, profileObis, dtos, cp
                        );

                metricsPort.recordBatch(
                        meterSerial,
                        profileObis,
                        syncResult.getInsertedCount(),
                        0
                );

                cursor = BillingDeterminantsIngestionSupport.nextCursorAfterBatch(syncResult, to);

                if (cp.seconds() <= 0) {
                    log.warn("cp.seconds() <= 0 : {}", cp);
                    return;
                }
            }

        } catch (Exception ex) {
            String safeObis = (profileObis == null || profileObis.isBlank()) ? "unknown" : profileObis;
            log.error("MonthlyBillingDataHousehold unhandled exception meter={} obis={}",
                    meterSerial, safeObis, ex);
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
