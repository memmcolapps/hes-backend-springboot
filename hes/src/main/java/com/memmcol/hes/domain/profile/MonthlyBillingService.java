package com.memmcol.hes.domain.profile;

import com.memmcol.hes.application.port.out.ProfileMetricsPort;
import com.memmcol.hes.application.port.out.ProfileReadException;
import com.memmcol.hes.application.port.out.ProfileStatePort;
import com.memmcol.hes.config.BillingDeterminantsProperties;
import com.memmcol.hes.domain.profile.mappers.MonthlyBillingMapper;
import com.memmcol.hes.dto.MonthlyBillingDTO;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.infrastructure.dlms.ProfileMetadataProvider;
import com.memmcol.hes.infrastructure.persistence.MonthlyBillingPersistenceAdapter;
import com.memmcol.hes.repository.MeterRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@AllArgsConstructor
@Slf4j
@Service
public class MonthlyBillingService {
    private final DlmsReaderUtils dlmsReaderUtils;
    private final ProfileMetricsPort metricsPort;
    private final ProfileStatePort statePort;
    private final ProfileMetadataProvider metadataProvider;
    private final MonthlyBillingMapper billingMapper;
    private final MonthlyBillingPersistenceAdapter monthlyBillingPersistenceAdapter;
    private final MeterRepository meterRepository;
    private final BillingDeterminantsProperties billingProperties;
    private final BillingCycleGracePeriodPolicy gracePeriodPolicy;

    public void readProfileAndSave(String model, String meterSerial, String profileObis, boolean isMD) {
        try {
            final String safeObis = (profileObis == null || profileObis.isBlank()) ? "unknown" : profileObis;

            if (profileObis == null || profileObis.isBlank()) {
                log.error("Profile OBIS is null/blank; skipping read meter={} model={}", meterSerial, model);
                metricsPort.recordFailure(meterSerial, safeObis, "missing_profile_obis");
                return;
            }

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

            if (gracePeriodPolicy.isWithinGracePeriodNow()) {
                log.debug("MonthlyBilling grace-period active meter={} seedFrom={} graceDays={}",
                        meterSerial, seedFrom, gracePeriodPolicy.gracePeriodDays());
            }

            while (cursor.value().isBefore(now)) {
                LocalDateTime from = cursor.value();
                LocalDateTime to = BillingDeterminantsIngestionSupport.computeWindowEnd(from, now, readWindowDays);

                long t0 = System.currentTimeMillis();
                boolean exceptionOccurred = false;

                final ProfileMetadataResult metadataResult;
                try {
                    metadataResult = metadataProvider.resolve(meterSerial, profileObis, model);
                } catch (Exception metaEx) {
                    log.error("Metadata resolve failed meter={} profile={} model={}",
                            meterSerial, profileObis, model, metaEx);
                    metricsPort.recordFailure(meterSerial, safeObis, "metadata_resolve_failed");
                    return;
                }

                List<ProfileRowGeneric> rawRows;
                try {
                    rawRows = dlmsReaderUtils.readRange(
                            model, meterSerial, profileObis, metadataResult, from, to, isMD
                    );
                } catch (Exception e) {
                    log.warn("Range read failed; attempting recovery meter={} profile={}",
                            meterSerial, profileObis, e);
                    exceptionOccurred = true;
                    rawRows = attemptRecovery(model, meterSerial, profileObis, metadataResult);
                }

                if (BillingDeterminantsIngestionSupport.shouldBreakOnEmptyWithException(exceptionOccurred, rawRows)) {
                    log.warn("Breaking (no rows + exception) meter={} profile={} from={} to={}",
                            meterSerial, profileObis, from, to);
                    return;
                }

                if (BillingDeterminantsIngestionSupport.isEmptyWindow(rawRows)) {
                    log.warn("Empty profile response meter={} profile={} from={} to={}",
                            meterSerial, profileObis, from, to);
                    cursor = BillingDeterminantsIngestionSupport.advanceInMemoryCursor(to);
                    continue;
                }

                List<MonthlyBillingDTO> dtos =
                        billingMapper.toDTO(rawRows, meterSerial, model, isMD, metadataResult);

                monthlyBillingPersistenceAdapter.createPartitionsIfMissing(dtos);

                ProfileSyncResult syncResult =
                        monthlyBillingPersistenceAdapter.saveBatchAndAdvanceCursor(
                                meterSerial, profileObis, dtos, cp
                        );

                metricsPort.recordBatch(
                        meterSerial,
                        profileObis,
                        syncResult.getInsertedCount(),
                        System.currentTimeMillis() - t0
                );

                cursor = BillingDeterminantsIngestionSupport.nextCursorAfterBatch(syncResult, to);

                if (cp.seconds() <= 0) {
                    log.warn("cp.seconds() <= 0 : {}", cp);
                    return;
                }
            }

        } catch (Exception ex) {
            log.error("Fatal exception while reading profile, meter={}, profile={}",
                    meterSerial, profileObis, ex);

            String safeObis = (profileObis == null || profileObis.isBlank()) ? "unknown" : profileObis;
            metricsPort.recordFailure(meterSerial, safeObis, "unhandled_exception");
        }
    }

    private List<ProfileRowGeneric> attemptRecovery(String model, String serial, String profileObis, ProfileMetadataResult metadataResult) {
        try {
            List<ProfileRowGeneric> salvaged = dlmsReaderUtils.recoverPartial(serial, profileObis, model, metadataResult);
            metricsPort.recordRecovery(serial, profileObis, salvaged.size());
            return salvaged;
        } catch (ProfileReadException e) {
            metricsPort.recordFailure(serial, profileObis, "recovery_failed");
            log.error("Partial recovery failed meter={} profile={}", serial, profileObis, e);
            return List.of();
        }
    }
}
