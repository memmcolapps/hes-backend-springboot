package com.memmcol.hes.domain.profile;

import com.memmcol.hes.application.port.out.ProfileMetricsPort;
import com.memmcol.hes.application.port.out.ProfileReadException;
import com.memmcol.hes.application.port.out.ProfileStatePort;
import com.memmcol.hes.domain.profile.mappers.BillingEnergyHouseholdMapper;
import com.memmcol.hes.dto.BillingEnergyHouseholdDTO;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.infrastructure.dlms.ProfileMetadataProvider;
import com.memmcol.hes.infrastructure.persistence.MonthlyBillingEnergyHouseholdPersistenceAdapter;
import com.memmcol.hes.repository.MeterRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@AllArgsConstructor
@Slf4j
@Service
public class MonthlyBillingEnergyHouseholdService {
    private final DlmsReaderUtils dlmsReaderUtils;
    private final ProfileMetricsPort metricsPort;
    private final ProfileStatePort statePort;
    private final ProfileMetadataProvider metadataProvider;
    private final BillingEnergyHouseholdMapper mapper;
    private final MonthlyBillingEnergyHouseholdPersistenceAdapter persistenceAdapter;
    private final MeterRepository meterRepository;

    public void readProfileAndSave(String model, String meterSerial, String profileObis, boolean isMD) {
        try {
            final String safeObis = (profileObis == null || profileObis.isBlank()) ? "unknown" : profileObis;
            if (profileObis == null || profileObis.isBlank()) {
                metricsPort.recordFailure(meterSerial, safeObis, "missing_profile_obis");
                return;
            }

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
                    cursor = new ProfileTimestamp(to).plus(cp);
                    statePort.upsertState(meterSerial, profileObis, new ProfileTimestamp(to), cp);
                    continue;
                }

                List<BillingEnergyHouseholdDTO> dtos = mapper.toDTO(rawRows, meterSerial, model, isMD, metadataResult);
                persistenceAdapter.createPartitionsIfMissing(dtos);
                ProfileSyncResult syncResult = persistenceAdapter.saveBatchAndAdvanceCursor(meterSerial, profileObis, dtos, cp);
                metricsPort.recordBatch(meterSerial, profileObis, syncResult.getInsertedCount(), 0);

                ProfileTimestamp resume = ProfileTimestamp.ofNullable(syncResult.getAdvanceTo());
                cursor = (resume != null) ? resume.plus(cp) : cursor.plus(cp);
                statePort.upsertState(meterSerial, profileObis, resume, cp);
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

