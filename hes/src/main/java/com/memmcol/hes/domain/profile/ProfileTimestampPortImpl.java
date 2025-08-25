package com.memmcol.hes.domain.profile;

import com.memmcol.hes.application.port.out.ProfileTimestampPort;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.nettyUtils.SessionManager;
import com.memmcol.hes.trackByTimestamp.MeterProfileState;
import com.memmcol.hes.trackByTimestamp.MeterProfileStateRepository;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXReplyData;
import gurux.dlms.objects.GXDLMSProfileGeneric;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileTimestampPortImpl implements ProfileTimestampPort {

    private static final String CACHE_NAME = "lastProfileTimestamp"; // define in Caffeine config
    private final MeterProfileStateRepository stateRepo;
    private final CacheManager cacheManager;
    private final SessionManager sessionManager;          // your existing DLMS session helper
    private final DlmsReaderUtils dlmsReaderUtils;
    private static final String CACHE_PREFIX = "lastTs::";

    @Override
    @Transactional
    public LocalDateTime resolveLastTimestamp(String meterSerial, String profileObis) {
        String key = cacheKey(meterSerial, profileObis);

        // 1. Cache lookup
        LocalDateTime cached = getCache().get(cacheKey(meterSerial, profileObis), LocalDateTime.class);
        if (cached != null) {
            log.debug("LastTimestamp cache hit meter={} obis={} ts={}", meterSerial, profileObis, cached);
            return cached;
        }

        // 2. DB lookup
        Optional<LocalDateTime> dbTs = stateRepo.findLastTimestamp(meterSerial, profileObis);
        if (dbTs.isPresent()) {
            LocalDateTime ts = dbTs.get();
            getCache().put(key, ts);
            log.debug("LastTimestamp DB hit meter={} obis={} ts={}", meterSerial, profileObis, ts);
            return ts;  // return immediately if present
        }

        // 3. Meter read
        try {
            LocalDateTime fromMeter = readFirstTimestampFromMeter(meterSerial, profileObis);
            if (fromMeter != null) {
                upsertLastTimestamp(meterSerial, profileObis, fromMeter);
                getCache().put(key, fromMeter);
                return fromMeter;
            }
        } catch (Exception ex) {
            log.error("Failed to read first timestamp from meter={} obis={}: {}", meterSerial, profileObis, ex.getMessage());
        }

        // 4. Fallback default to Yesterday
        LocalDateTime fallback = new ProfileTimestamp(LocalDateTime.now().minusDays(1)).value();
        log.warn("Unable to determine last timestamp for meter={} obis={}, using fallback={}", meterSerial, profileObis, fallback);
        return fallback;
    }

    private String cacheKey(String meterSerial, String obis) {
        return CACHE_PREFIX + meterSerial + "::" + obis;
    }

    @Transactional
    protected void upsertLastTimestamp(String meterSerial,
                                       String profileObis,
                                       LocalDateTime ts) {

        MeterProfileState state = stateRepo
                .findByMeterSerialAndProfileObis(meterSerial, profileObis)
                .orElseGet(() -> MeterProfileState.builder()
                        .meterSerial(meterSerial)
                        .profileObis(profileObis)
                        .capturePeriodSec(null)      // leave unknown
                        .build());

        state.setLastTimestamp(ts);
        state.setUpdatedAt(LocalDateTime.now());

        stateRepo.save(state);  // insert or update
    }

    /**
     * Queries the meter for the first available timestamp (row 1) in the profile buffer.
     * Only placeholder logic here; implement Gurux read using readRowsByEntry().
     */
    private LocalDateTime readFirstTimestampFromMeter(String meterSerial, String profileObis) {
        try {
            GXDLMSClient client = sessionManager.getOrCreateClient(meterSerial);
            if (client == null) {
                log.warn("No DLMS client available for meter {}", meterSerial);
                return null;
            }

            GXDLMSProfileGeneric profile = new GXDLMSProfileGeneric();
            profile.setLogicalName(profileObis);

            // Step 1: Read Capture Objects (Attribute 3)
            byte[][] captureRequest1 = client.read(profile, 3);
            GXReplyData captureReply1 =  dlmsReaderUtils.readDataBlock(client, meterSerial, captureRequest1[0]);
            client.updateValue(profile, 3, captureReply1.getValue());

            // Read only the first row (1 record)
            byte[][] req = client.readRowsByEntry(profile, 1, 1);
            GXReplyData reply = dlmsReaderUtils.readDataBlock(client, meterSerial, req[0]);
            client.updateValue(profile, 2, reply.getValue()); // 2 = buffer
            LocalDateTime ts = dlmsReaderUtils.extractFirstRowTimestampDirect(profile);
            return ts;

        } catch (Exception ex) {
            log.error("Error reading first timestamp from meter {}: {}", meterSerial, ex.getMessage());
        }
        return null;
    }

    private Cache getCache() {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) {
            log.debug("Cache {} not configured", CACHE_NAME);
        }
        return cache;
    }

    private void putCache(String meterSerial, String profileObis, int cp) {
        Cache cache = getCache();
        if (cache != null) cache.put(cacheKey(meterSerial, profileObis), cp);
    }

}

