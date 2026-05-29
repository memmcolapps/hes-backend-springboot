package com.memmcol.hes.jobs.workers;

import com.memmcol.hes.domain.profile.MetersLockService;
import com.memmcol.hes.dto.MeterDTO;
import com.memmcol.hes.repository.MeterRepository;
import com.memmcol.hes.service.MeterConnections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;

/**
 * Class B: Incremental Worker
 * Focuses on ongoing heavy data streams or backlogs, continuing from last checkpoint.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClassB_IncrementalWorker {

    private final MeterRepository meterRepository;
    private final MetersLockService metersLockService;

    public void processBacklog(String obisCode, LocalTime cutoff) {
        log.info("ClassB: Starting incremental backlog processing for OBIS {}", obisCode);

        java.util.Collection<String> activeSerials = MeterConnections.getAllActiveSerials();
        if (activeSerials.isEmpty()) return;

        for (String serial : activeSerials) {
            if (LocalTime.now().isAfter(cutoff) && LocalTime.now().isBefore(LocalTime.of(22, 0))) {
                log.warn("ClassB: Nightly window closed. Saving progress and stopping.");
                break;
            }

            meterRepository.findMeterDetailsByMeterNumber(serial).ifPresent(dto -> {
                log.debug("ClassB: Processing incremental data for meter {}", serial);
                // Standard sync methods in HES already use ProfileState (checkpoints).
                // They process data in batches and advance the cursor only on success.
                invokeIncrementalSync(dto, obisCode);
            });
        }
    }

    private void invokeIncrementalSync(MeterDTO dto, String obis) {
        if ("0.1.24.3.0.255".equals(obis)) {
            metersLockService.readChannelOneHouseholdWithLock(dto.getMeterModel(), dto.getMeterNumber(), obis, dto.isMD());
        } else if ("0.2.24.3.0.255".equals(obis)) {
            metersLockService.readChannelTwoHouseholdWithLock(dto.getMeterModel(), dto.getMeterNumber(), obis, dto.isMD());
        } else if ("0.0.99.2.0.255".equals(obis)) {
            metersLockService.readDailyBillingDataHouseholdWithLock(dto.getMeterModel(), dto.getMeterNumber(), obis, dto.isMD());
        } else {
            // Generic fallback
            metersLockService.readChannelThreeHouseholdWithLock(dto.getMeterModel(), dto.getMeterNumber(), obis, dto.isMD());
        }
    }
}
