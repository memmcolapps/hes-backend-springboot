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
 * Class A: Replay Worker
 * Targets the last 24 hours to repair data gaps or failures.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClassA_ReplayWorker {

    private final MeterRepository meterRepository;
    private final MetersLockService metersLockService;

    public void repairGaps(String obisCode, LocalTime cutoff) {
        log.info("ClassA: Starting data repair for OBIS {}", obisCode);

        java.util.Collection<String> activeSerials = MeterConnections.getAllActiveSerials();
        if (activeSerials.isEmpty()) return;

        for (String serial : activeSerials) {
            if (LocalTime.now().isAfter(cutoff) && LocalTime.now().isBefore(LocalTime.of(22, 0))) {
                log.warn("ClassA: Nightly window closed. Aborting repair for remaining meters.");
                break;
            }

            meterRepository.findMeterDetailsByMeterNumber(serial).ifPresent(dto -> {
                log.debug("ClassA: Repairing gaps for meter {}", serial);
                // In a real scenario, we might check for gaps first.
                // For this orchestration, we trigger the standard sync which is idempotent
                // and naturally repairs gaps due to its watermark/cursor logic.
                invokeSync(dto, obisCode);
            });
        }
    }

    private void invokeSync(MeterDTO dto, String obis) {
        // Logic to select the right sync method based on OBIS or type
        if ("1.0.99.1.0.255".equals(obis)) {
            metersLockService.readChannelOneWithLock(dto.getMeterModel(), dto.getMeterNumber(), obis, dto.isMD());
        } else if ("1.0.99.2.0.255".equals(obis)) {
            metersLockService.readChannelTwoWithLock(dto.getMeterModel(), dto.getMeterNumber(), obis, dto.isMD());
        } else {
            // Default to general event or profile read
            metersLockService.readEventsWithLock(dto.getMeterModel(), dto.getMeterNumber(), obis, dto.isMD());
        }
    }
}
