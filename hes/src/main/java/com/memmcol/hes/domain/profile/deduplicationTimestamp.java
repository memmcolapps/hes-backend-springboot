package com.memmcol.hes.domain.profile;

import com.memmcol.hes.model.ProfileChannel2ReadingDTO;
import com.memmcol.hes.repository.ProfileChannel2Repository;
import com.memmcol.hes.trackByTimestamp.MeterProfileTimestampProgressRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@AllArgsConstructor
public class deduplicationTimestamp {
    private final ProfileChannel2Repository channel2Repo;

    public List<ProfileChannel2ReadingDTO> deduplicate(List<ProfileChannel2ReadingDTO> readings, String meterSerial){
        // 📌 Deduplication by timestamp only
        List<LocalDateTime> incomingTimestamps = readings.stream()
                .map(ProfileChannel2ReadingDTO::getEntryTimestamp)
                .toList();

        List<LocalDateTime> existingTimestamps = channel2Repo
                .findExistingTimestamps(meterSerial, incomingTimestamps);

        List<ProfileChannel2ReadingDTO> newDtos = readings.stream()
                .filter(dto -> !existingTimestamps.contains(dto.getEntryTimestamp()))
                .toList();

        if (newDtos.isEmpty()) {
            log.info("✅ No new readings to save — all timestamps already exist.");
            return List.of();
        }

        return newDtos;
    }
}
