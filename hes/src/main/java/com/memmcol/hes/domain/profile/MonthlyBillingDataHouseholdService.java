package com.memmcol.hes.domain.profile;

import com.memmcol.hes.application.port.out.ProfileMetricsPort;
import com.memmcol.hes.application.port.out.ProfileReadException;
import com.memmcol.hes.application.port.out.ProfileStatePort;
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

    public void readProfileAndSave(String model, String meterSerial, String profileObis, boolean isMD) {
        try {
            final String safeObis = (profileObis == null || profileObis.isBlank()) ? "unknown" : profileObis;
            if (profileObis == null || profileObis.isBlank()) {
                metricsPort.recordFailure(meterSerial, safeObis, "missing_profile_obis");
                return;
            }

            log.info("MonthlyBillingDataHousehold read start. meter={} model={} obis={} md={}", meterSerial, model, safeObis, isMD);

            LocalDateTime seedFrom = null;
            ProfileState st = statePort.loadState(meterSerial, profileObis);
            if (st != null && st.lastTimestamp() != null) seedFrom = st.lastTimestamp().value();
            if (seedFrom == null) {
                seedFrom = meterRepository.findMeterDetailsByMeterNumber(meterSerial)
                        .map(m -> m.getCreatedAt())
                        .orElse(null);
            }
            if (seedFrom == null) {
                seedFrom = LocalDateTime.now()
                        .minusMonths(1)
                        .withDayOfMonth(1)
                        .withHour(0).withMinute(0).withSecond(0).withNano(0);
            }

            ProfileTimestamp cursor = new ProfileTimestamp(seedFrom);
            CapturePeriod cp = new CapturePeriod(1);
            LocalDateTime now = LocalDateTime.now();

            while (cursor.value().isBefore(now)) {
                LocalDateTime from = cursor.value();
                LocalDateTime to = from.plusMonths(12);
                if (to.isAfter(now)) to = now;

                log.info("MonthlyBillingDataHousehold range. meter={} obis={} from={} to={}", meterSerial, safeObis, from, to);

                boolean exceptionOccurred = false;
                final ProfileMetadataResult metadataResult;
                try {
                    metadataResult = metadataProvider.resolve(meterSerial, profileObis, model);
                } catch (Exception metaEx) {
                    log.error("MonthlyBillingDataHousehold metadata resolve failed. meter={} obis={} model={}", meterSerial, safeObis, model, metaEx);
                    metricsPort.recordFailure(meterSerial, safeObis, "metadata_resolve_failed");
                    return;
                }

                List<ProfileRowGeneric> rawRows;
                try {
                    rawRows = dlmsReaderUtils.readRange(model, meterSerial, profileObis, metadataResult, from, to, isMD);
                } catch (Exception e) {
                    exceptionOccurred = true;
                    log.warn("MonthlyBillingDataHousehold range read failed; attempting recovery. meter={} obis={} model={}", meterSerial, safeObis, model, e);
                    rawRows = attemptRecovery(model, meterSerial, profileObis, metadataResult);
                }

                if ((rawRows == null || rawRows.isEmpty()) && exceptionOccurred) return;
                if (rawRows == null || rawRows.isEmpty()) {
                    cursor = new ProfileTimestamp(to).plus(cp);
                    statePort.upsertState(meterSerial, profileObis, new ProfileTimestamp(to), cp);
                    continue;
                }

                List<BillingDataHouseholdDTO> dtos = mapper.toDTO(rawRows, meterSerial, model, isMD, metadataResult);
                log.info("MonthlyBillingDataHousehold mapped rows. meter={} obis={} rawRows={} dtos={}", meterSerial, safeObis, rawRows.size(), dtos.size());
                persistenceAdapter.createPartitionsIfMissing(dtos);
                ProfileSyncResult syncResult = persistenceAdapter.saveBatchAndAdvanceCursor(meterSerial, profileObis, dtos, cp);
                log.info("MonthlyBillingDataHousehold saved batch. meter={} obis={} inserted={} duplicate={} prevLast={} incomingMax={} advanceTo={} advanced={}",
                        meterSerial, safeObis,
                        syncResult.getInsertedCount(),
                        syncResult.getDuplicateCount(),
                        syncResult.getPreviousLast(),
                        syncResult.getIncomingMax(),
                        syncResult.getAdvanceTo(),
                        syncResult.isAdvanced());
                metricsPort.recordBatch(meterSerial, profileObis, syncResult.getInsertedCount(), 0);

                ProfileTimestamp resume = ProfileTimestamp.ofNullable(syncResult.getAdvanceTo());
                cursor = (resume != null) ? resume.plus(cp) : cursor.plus(cp);
                statePort.upsertState(meterSerial, profileObis, resume, cp);
            }
        } catch (Exception ex) {
            String safeObis = (profileObis == null || profileObis.isBlank()) ? "unknown" : profileObis;
            log.error("MonthlyBillingDataHousehold unhandled exception. meter={} obis={}", meterSerial, safeObis, ex);
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

