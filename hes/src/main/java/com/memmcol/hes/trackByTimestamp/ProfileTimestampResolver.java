package com.memmcol.hes.trackByTimestamp;

import com.memmcol.hes.service.MeterReadAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProfileTimestampResolver {

    private final ProfileTimestampCacheService cacheService;
    private final MeterProfileStateService stateService;
    private final MeterReadAdapter readAdapter;

    public LocalDateTime resolveStartTimestamp(String serial, String profileObis, String model) throws Exception {
        // 1. cache
        LocalDateTime ts = cacheService.get(serial, profileObis);
        if (ts != null) return ts;

        // 2. DB
        ts = stateService.getLastTimestamp(serial, profileObis);
        if (ts != null) {
            cacheService.put(serial, profileObis, ts);
            return ts;
        }

        // 3. MetersEntity (bootstrap)
        return readAdapter.bootstrapFromMeter(serial, profileObis, model);
    }

    // bootstrapFromMeter shown above (Step 1)
}
