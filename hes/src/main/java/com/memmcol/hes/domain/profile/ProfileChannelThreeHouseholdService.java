package com.memmcol.hes.domain.profile;

import com.memmcol.hes.application.port.out.CapturePeriodPort;
import com.memmcol.hes.application.port.out.ProfileMetricsPort;
import com.memmcol.hes.application.port.out.ProfileReadException;
import com.memmcol.hes.application.port.out.ProfileStatePort;
import com.memmcol.hes.domain.profile.mappers.ProfileChannelThreeHouseholdMapper;
import com.memmcol.hes.dto.ProfileChannelThreeHouseholdDTO;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.infrastructure.dlms.ProfileMetadataProvider;
import com.memmcol.hes.infrastructure.persistence.ProfileChannelThreeHouseholdPersistenceAdapter;
import com.memmcol.hes.repository.MeterRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@AllArgsConstructor
@Slf4j
@Service
public class ProfileChannelThreeHouseholdService {
    private final CapturePeriodPort capturePeriodPort;
    private final DlmsReaderUtils dlmsReaderUtils;
    private final ProfileMetricsPort metricsPort;
    private final ProfileStatePort statePort;
    private final ProfileMetadataProvider metadataProvider;
    private final ProfileChannelThreeHouseholdPersistenceAdapter persistenceAdapter;
    private final ProfileChannelThreeHouseholdMapper mapper;
    private final MeterRepository meterRepository;

    public void readProfileAndSaveV2(String model,
                                   String meterSerial,
                                   String profileObis,
                                   boolean isMD) {

        final String safeObis =
                (profileObis == null || profileObis.isBlank())
                        ? "unknown"
                        : profileObis;

        try {

            // =====================================================
            // VALIDATION
            // =====================================================
            if (profileObis == null || profileObis.isBlank()) {
                log.error("Profile OBIS is null/blank; skipping read meter={} model={}",
                        meterSerial, model);

                metricsPort.recordFailure(
                        meterSerial,
                        safeObis,
                        "missing_profile_obis"
                );

                return;
            }

            // =====================================================
            // LOAD WATERMARK (SOURCE OF TRUTH)
            // =====================================================
            LocalDateTime seedFrom = null;

            ProfileState st = statePort.loadState(meterSerial, profileObis);

            if (st != null && st.lastTimestamp() != null) {
                seedFrom = st.lastTimestamp().value();
            }

            if (seedFrom == null) {
                seedFrom = meterRepository.findMeterDetailsByMeterNumber(meterSerial)
                        .map(m -> m.getCreatedAt())
                        .orElse(null);
            }

            if (seedFrom == null) {
                seedFrom = LocalDateTime.now().minusDays(1);
            }

            // IMPORTANT: watermark cursor ONLY
            ProfileTimestamp cursor = new ProfileTimestamp(seedFrom);

            // =====================================================
            // CAPTURE PERIOD
            // =====================================================
            CapturePeriod cp = new CapturePeriod(
                    capturePeriodPort.resolveCapturePeriodSeconds(
                            meterSerial,
                            profileObis
                    )
            );

            if (cp.seconds() <= 0) {
                log.warn("Invalid capture period meter={} profile={} cp={}",
                        meterSerial, profileObis, cp.seconds());

                cp = new CapturePeriod(3600);
            }

            if (cp.seconds() < 3600) {
                cp = new CapturePeriod(3600);
            }

            final LocalDateTime now = LocalDateTime.now();

            // =====================================================
            // REPLAY LOOP
            // =====================================================
            while (cursor.value().isBefore(now)) {

                final LocalDateTime from = cursor.value();
                LocalDateTime to = from.plusDays(1);

                if (to.isAfter(now)) {
                    to = now;
                }

                long t0 = System.currentTimeMillis();

                boolean recoveryAttempted = false;

                // =================================================
                // METADATA
                // =================================================
                final ProfileMetadataResult metadataResult;

                try {
                    metadataResult = metadataProvider.resolve(
                            meterSerial,
                            profileObis,
                            model
                    );
                } catch (Exception ex) {

                    log.error("Metadata resolve failed meter={} profile={} model={}",
                            meterSerial, profileObis, model, ex);

                    metricsPort.recordFailure(
                            meterSerial,
                            safeObis,
                            "metadata_resolve_failed"
                    );

                    return;
                }

                // =================================================
                // READ RANGE
                // =================================================
                List<ProfileRowGeneric> rows;

                try {
                    rows = dlmsReaderUtils.readRange(
                            model,
                            meterSerial,
                            profileObis,
                            metadataResult,
                            from,
                            to,
                            isMD
                    );
                } catch (Exception ex) {

                    recoveryAttempted = true;

                    log.warn("Range read failed meter={} profile={} from={} to={}",
                            meterSerial, profileObis, from, to, ex);

                    rows = attemptRecovery(
                            model,
                            meterSerial,
                            profileObis,
                            metadataResult
                    );
                }

                // =================================================
                // EMPTY HANDLING (NO CURSOR ADVANCE)
                // =================================================
                if (rows == null || rows.isEmpty()) {
                     log.warn("Empty profile response meter={} profile={} from={} to={}",
                            meterSerial, profileObis, from, to);
                    cursor = new ProfileTimestamp(to);   // ALWAYS move forward deterministically
                    continue;
                }

                // =================================================
                // MAP DTOs
                // =================================================
                List<ProfileChannelThreeHouseholdDTO> dtos =
                        mapper.toDTO(
                                rows,
                                meterSerial,
                                model,
                                isMD,
                                metadataResult
                        );

                if (dtos == null || dtos.isEmpty()) {
                    log.warn("DTO mapping empty meter={} profile={}",
                            meterSerial, profileObis);

                    break;
                }

                // =================================================
                // PARTITIONS
                // =================================================
                persistenceAdapter.createPartitionsIfMissing(dtos);

                // =================================================
                // PERSIST + WATERMARK ADVANCE
                // =================================================
                ProfileSyncResult syncResult =
                        persistenceAdapter.saveBatchAndAdvanceCursor(
                                meterSerial,
                                profileObis,
                                dtos,
                                cp
                        );

                long duration = System.currentTimeMillis() - t0;

                metricsPort.recordBatch(
                        meterSerial,
                        profileObis,
                        syncResult.getInsertedCount(),
                        duration
                );

                // =================================================
                // WATERMARK VALIDATION
                // =================================================
                ProfileTimestamp resume = ProfileTimestamp.ofNullable(syncResult.getAdvanceTo());

                cursor = (resume != null)
              ? resume
              : new ProfileTimestamp(to);

                log.info("Profile3HH advanced meter={} profile={} cursor={} inserted={} durationMs={}",
                        meterSerial,
                        profileObis,
                        cursor.value(),
                        syncResult.getInsertedCount(),
                        duration);
            }

        } catch (Exception ex) {

            log.error("Fatal exception reading profile3HH meter={} profile={}",
                    meterSerial, profileObis, ex);

            metricsPort.recordFailure(
                    meterSerial,
                    safeObis,
                    "unhandled_exception"
            );
        }
    }

    public void readProfileAndSave(String model, String meterSerial, String profileObis, boolean isMD) {
        try {
            final String safeObis = (profileObis == null || profileObis.isBlank()) ? "unknown" : profileObis;

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
                        .map(m -> m.getCreatedAt())
                        .orElse(null);
            }
            if (seedFrom == null) {
                seedFrom = LocalDateTime.now().minusDays(1);
            }
            ProfileTimestamp cursor = new ProfileTimestamp(seedFrom);

            CapturePeriod cp = new CapturePeriod(capturePeriodPort.resolveCapturePeriodSeconds(meterSerial, profileObis));
            if (cp.seconds() < 3600) {
                cp = new CapturePeriod(3600);
            }

            LocalDateTime now = LocalDateTime.now();

            while (cursor.value().isBefore(now)) {
                LocalDateTime from = cursor.value();
                LocalDateTime to = from.plusDays(1);
                if (to.isAfter(now)) to = now;

                long t0 = System.currentTimeMillis();
                boolean exceptionOccurred = false;

                final ProfileMetadataResult metadataResult;
                try {
                    metadataResult = metadataProvider.resolve(meterSerial, profileObis, model);
                } catch (Exception metaEx) {
                    log.error("Metadata resolve failed meter={} profile={} model={} cause={}",
                            meterSerial, profileObis, model, metaEx.getMessage(), metaEx);
                    metricsPort.recordFailure(meterSerial, safeObis, "metadata_resolve_failed");
                    return;
                }

                List<ProfileRowGeneric> rows;
                try {
                    rows = dlmsReaderUtils.readRange(model, meterSerial, profileObis, metadataResult, from, to, isMD);
                } catch (Exception ex) {
                    log.warn("Range read failed; attempting recovery meter={} profile={} cause={}",
                            meterSerial, profileObis, ex.getMessage());
                    exceptionOccurred = true;
                    rows = attemptRecovery(model, meterSerial, profileObis, metadataResult);
                }

                if ((rows == null || rows.isEmpty()) && exceptionOccurred) {
                    log.warn("Breaking (no rows + exception) meter={} profile={} from={} to={}",
                            meterSerial, profileObis, from, to);
                    return;
                }

                if (rows == null || rows.isEmpty()) {
                    log.warn("Empty profile response meter={} profile={} from={} to={}",
                            meterSerial, profileObis, from, to);
                    cursor = new ProfileTimestamp(to); // ALWAYS move forward deterministically
                    continue;
                }

                List<ProfileChannelThreeHouseholdDTO> dtos = mapper.toDTO(rows, meterSerial, model, isMD, metadataResult);
                persistenceAdapter.createPartitionsIfMissing(dtos);
                ProfileSyncResult syncResult = persistenceAdapter.saveBatchAndAdvanceCursor(meterSerial, profileObis, dtos, cp);

                long duration = System.currentTimeMillis() - t0;
                metricsPort.recordBatch(meterSerial, profileObis, syncResult.getInsertedCount(), duration);

                ProfileTimestamp resume = ProfileTimestamp.ofNullable(syncResult.getAdvanceTo());
                cursor = (resume != null) ? resume.plus(cp) : new ProfileTimestamp(to);
//                statePort.upsertState(meterSerial, profileObis, resume, cp);

                if (cp.seconds() <= 0) {
                    log.warn("cp.seconds() <= 0 : {}", cp);
                    return;
                }
            }
        } catch (Exception ex) {
            log.error("Fatal exception while reading household profile, meter={}, profile={}: {}",
                    meterSerial, profileObis, ex.getMessage(), ex);
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
