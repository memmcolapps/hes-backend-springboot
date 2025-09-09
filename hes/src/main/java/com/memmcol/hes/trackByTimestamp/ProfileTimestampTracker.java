package com.memmcol.hes.trackByTimestamp;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.memmcol.hes.repository.ProfileChannel2Repository;
import com.memmcol.hes.service.MeterReadAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ProfileTimestampTracker {


    private final CacheManager cacheManager;
    private final MeterProfileTimestampProgressRepository timestampProgressRepository;
    private final ProfileChannel2Repository channel2Repository;
    private final MeterReadAdapter readAdapter;

    private Cache getCache() {
        return cacheManager.getCache("lastProfileTimestamp");
    }


    public LocalDateTime getLastTimestamp(String serial, String obis) {
        return timestampProgressRepository.findByMeterSerialAndProfileObis(serial, obis)
                .map(MeterProfileTimestampProgress::getLastProfileTimestamp)
                .orElse(LocalDateTime.MIN); // Start from the beginning
    }

    public void updateLastTimestamp(String serial, String obis, LocalDateTime timestamp) {
        MeterProfileTimestampProgress progress = timestampProgressRepository.findByMeterSerialAndProfileObis(serial, obis)
                .orElse(MeterProfileTimestampProgress.builder()
                        .meterSerial(serial)
                        .profileObis(obis)
                        .lastProfileTimestamp(timestamp)
                        .updatedAt(LocalDateTime.now())
                        .build());

        progress.setLastProfileTimestamp(timestamp);
        progress.setUpdatedAt(LocalDateTime.now());

        timestampProgressRepository.save(progress);
    }

    public LocalDateTime getLastTimestamp(String serial, String obis, String model) throws Exception {
        // ✅ Try cache
        Cache.ValueWrapper wrapper = getCache().get("lastProfileTimestamp:" + serial);
        if (wrapper != null && wrapper.get() instanceof LocalDateTime ts) {
            return ts;
        }

        // ✅ Try DB
        LocalDateTime dbTimestamp = channel2Repository.findLatestTimestamp(serial);
        if (dbTimestamp != null) {
            getCache().put("lastProfileTimestamp:" + serial, dbTimestamp); // 🧠 cache it
            return dbTimestamp;
        }

        // ⚠️ Else return null — fallback to meter
        return readAdapter.bootstrapFromMeter(serial, obis, model);
//        return null;
    }

    public void updateLastTimestamp(String serial, LocalDateTime timestamp) {
        getCache().put("lastProfileTimestamp:" + serial, timestamp);
    }

}
