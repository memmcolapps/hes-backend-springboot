package com.memmcol.hes.trackLastEntryRead;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileProgressTracker {

    private final MeterProfileProgressRepository repo;

    public int getLastRead(String meterSerial, String profileObis) {
        int startIndex = repo.findByMeterSerialAndProfileObis(meterSerial, profileObis)
                .map(MeterProfileProgress::getLastEntryIndex)
                .orElse(1); // Default starting point
        log.info("startIndex: {}", startIndex);
        return startIndex;
    }

    public void updateLastRead(String meterSerial, String profileObis, int lastIndex) {
        MeterProfileProgress record = repo.findByMeterSerialAndProfileObis(meterSerial, profileObis)
                .orElse(MeterProfileProgress.builder()
                        .meterSerial(meterSerial)
                        .profileObis(profileObis)
                        .lastEntryIndex(lastIndex)
                        .updatedAt(LocalDateTime.now())
                        .build());

        record.setLastEntryIndex(lastIndex);
        record.setUpdatedAt(LocalDateTime.now());

        repo.save(record);
        log.info("last read updated at {}", record.getLastEntryIndex());
        log.info("lastIndex passed: {}", lastIndex);
    }
}
