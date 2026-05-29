package com.memmcol.hes.domain.profile;

import com.memmcol.hes.application.port.out.CapturePeriodPort;
import com.memmcol.hes.application.port.out.ProfileMetricsPort;
import com.memmcol.hes.application.port.out.ProfileReadException;
import com.memmcol.hes.application.port.out.ProfileStatePort;
import com.memmcol.hes.domain.profile.mappers.ProfileChannelOneHouseholdMapper;
import com.memmcol.hes.dto.MeterDTO;
import com.memmcol.hes.dto.ProfileChannelOneHouseholdDTO;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.infrastructure.dlms.ProfileMetadataProvider;
import com.memmcol.hes.infrastructure.persistence.ProfileChannelOneHouseholdPersistAdapter;
import com.memmcol.hes.repository.MeterRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@AllArgsConstructor
@Slf4j
@Service
public class ProfileChannelOneHouseholdService {
    private final CapturePeriodPort capturePeriodPort;
    private final DlmsReaderUtils dlmsReaderUtils;
    private final ProfileChannelOneHouseholdMapper mapper;
    private final ProfileChannelOneHouseholdPersistAdapter persistAdapter;
    private final ProfileMetricsPort metricsPort;
    private final ProfileStatePort statePort;
    private final ProfileMetadataProvider metadataProvider;
    private final MeterRepository meterRepository;

    public void readProfileAndSave(String model,
                                   String meterSerial,
                                   String profileObis,
                                   boolean isMD) {

        final String safeObis = (profileObis == null || profileObis.isBlank()) ? "unknown" : profileObis;

        try {
            if (profileObis == null || profileObis.isBlank()) {
                log.error("Profile OBIS is null/blank; skipping read meter={} model={}", meterSerial, model);
                metricsPort.recordFailure(meterSerial, safeObis, "missing_profile_obis");
                return;
            }

            LocalDateTime seedFrom = null;
            ProfileState st = statePort.loadState(meterSerial, profileObis);
            if (st != null && st.lastTimestamp() != null) {
                seedFrom = st.lastTimestamp().value();
            }

            if (seedFrom == null) {
                seedFrom = meterRepository.findMeterDetailsByMeterNumber(meterSerial)
                        .map(MeterDTO::getCreatedAt)
                        .orElse(null);
            }

            if (seedFrom == null) {
                seedFrom = LocalDateTime.now().minusDays(1);
            }

            ProfileTimestamp cursor = new ProfileTimestamp(seedFrom);

            CapturePeriod cp = new CapturePeriod(capturePeriodPort.resolveCapturePeriodSeconds(meterSerial, profileObis));
            if (cp.seconds() <= 0) {
                log.warn("Invalid capture period meter={} profile={} cp={}", meterSerial, profileObis, cp.seconds());
                cp = new CapturePeriod(3600);
            }
            if (cp.seconds() < 3600) {
                cp = new CapturePeriod(3600);
            }

            final LocalDateTime now = LocalDateTime.now();

            while (cursor.value().isBefore(now)) {
                final LocalDateTime from = cursor.value();
                LocalDateTime to = from.plusDays(1);
                if (to.isAfter(now)) to = now;

                long t0 = System.currentTimeMillis();
                boolean recoveryAttempted = false;

                final ProfileMetadataResult metadataResult;
                try {
                    metadataResult = metadataProvider.resolve(meterSerial, profileObis, model);
                } catch (Exception metaEx) {
                    log.error("Metadata resolve failed meter={} profile={} model={} cause={}",
                            meterSerial, profileObis, model, metaEx.getMessage(), metaEx);
                    metricsPort.recordFailure(meterSerial, safeObis, "metadata_resolve_failed");
                    return;
                }

                List<ProfileRowGeneric> rawRows;
                try {
                    rawRows = dlmsReaderUtils.readRange(model, meterSerial, profileObis, metadataResult, from, to, isMD);
                } catch (Exception ex) {
                    recoveryAttempted = true;
                    log.warn("Range read failed; attempting recovery meter={} profile={} from={} to={} cause={}",
                            meterSerial, profileObis, from, to, ex.getMessage());

                    rawRows = attemptRecovery(model, meterSerial, profileObis, metadataResult);
                }

                if (rawRows == null || rawRows.isEmpty()) {
                    if (recoveryAttempted) {
                        log.warn("Recovery failed with empty response meter={} profile={} from={} to={}",
                                meterSerial, profileObis, from, to);
                    } else {
                        log.info("Empty profile response meter={} profile={} from={} to={}",
                                meterSerial, profileObis, from, to);
                    }
                    break;
                }

                List<ProfileChannelOneHouseholdDTO> dtos = mapper.toDTO(rawRows, meterSerial, model, isMD, metadataResult);
                if (dtos == null || dtos.isEmpty()) {
                    log.warn("DTO mapping produced no rows meter={} profile={} from={} to={}",
                            meterSerial, profileObis, from, to);
                    break;
                }

                persistAdapter.createPartitionsIfMissing(dtos);
                ProfileSyncResult syncResult = persistAdapter.saveBatchAndAdvanceCursor(meterSerial, profileObis, dtos, cp);

                long duration = System.currentTimeMillis() - t0;
                metricsPort.recordBatch(meterSerial, profileObis, syncResult.getInsertedCount(), duration);

                ProfileTimestamp resume = ProfileTimestamp.ofNullable(syncResult.getAdvanceTo());
                if (resume == null || resume.value() == null) {
                    log.warn("Persistence returned null advanceTo meter={} profile={}", meterSerial, profileObis);
                    break;
                }

                if (!resume.value().isAfter(cursor.value())) {
                    log.warn("Non-advancing cursor detected meter={} profile={} cursor={} resume={}",
                            meterSerial, profileObis, cursor.value(), resume.value());
                    break;
                }

                cursor = resume;
                log.info("Profile1HH replay advanced meter={} profile={} from={} to={} nextCursor={} inserted={} duplicates={}",
                        meterSerial, profileObis, from, to, cursor.value(), syncResult.getInsertedCount(), syncResult.getDuplicateCount());
            }
        } catch (Exception ex) {
            log.error("Fatal exception while reading household profile1HH meter={} profile={} cause={}",
                    meterSerial, profileObis, ex.getMessage(), ex);
            metricsPort.recordFailure(meterSerial, safeObis, "unhandled_exception");
        }
    }

    private List<ProfileRowGeneric> attemptRecovery(String model, String serial, String profileObis, ProfileMetadataResult metadataResult) {
        try {
            List<ProfileRowGeneric> salvaged = dlmsReaderUtils.recoverPartial(serial, profileObis, model, metadataResult);
            String safeObis = (profileObis == null || profileObis.isBlank()) ? "unknown" : profileObis;
            metricsPort.recordRecovery(serial, safeObis, salvaged.size());
            return salvaged;
        } catch (ProfileReadException e) {
            String safeObis = (profileObis == null || profileObis.isBlank()) ? "unknown" : profileObis;
            metricsPort.recordFailure(serial, safeObis, "recovery_failed");
            log.error("Partial recovery failed meter={} profile={}", serial, profileObis, e);
            return List.of();
        }
    }
}
