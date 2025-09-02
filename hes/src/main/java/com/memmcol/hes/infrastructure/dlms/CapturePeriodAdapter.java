package com.memmcol.hes.infrastructure.dlms;

import com.memmcol.hes.application.port.out.CapturePeriodPort;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static com.memmcol.hes.nettyUtils.RequestResponseService.logTx;

@Component
@RequiredArgsConstructor
@Slf4j
public class CapturePeriodAdapter implements CapturePeriodPort {

    private static final String CACHE_NAME = "profileCapturePeriod"; // define in Caffeine config
    private final CacheManager cacheManager;
    private final MeterProfileStateRepository stateRepo;
    private final SessionManager sessionManager;          // your existing DLMS session helper
    private final DlmsReaderUtils dlmsReaderUtils;

    @Override
    @Transactional
    public int resolveCapturePeriodSeconds(String meterSerial, String profileObis) {
        // 1. Cache
        Integer cached = getCache().get(cacheKey(meterSerial, profileObis), Integer.class);
        if (cached != null && cached > 0) {
            log.debug("CapturePeriod cache hit meter={} obis={} cp={}s", meterSerial, profileObis, cached);
            return cached;
        }

        // 2. DB
        Optional<Integer> db = stateRepo.findCapturePeriodSec(meterSerial, profileObis);
        if (db.isPresent() && db.get() != null && db.get() > 0) {
            log.debug("CapturePeriod DB hit meter={} obis={} cp={}s", meterSerial, profileObis, db.get());
            putCache(meterSerial, profileObis, db.get());
            return db.get();
        }

        // 3. Meter read
        String msg = String.format("Reading capture period from meter=%s obis=%s", meterSerial, profileObis);
        log.info(msg);
        logTx(meterSerial, msg);

        int fromMeter = readCapturePeriodFromMeter(meterSerial, profileObis);
        if (fromMeter > 0) {
            // persist (insert or update)
            upsertCapturePeriod(meterSerial, profileObis, fromMeter);
            putCache(meterSerial, profileObis, fromMeter);
            return fromMeter;
        }

        // 4. Fallback
        log.warn("Unable to determine capture period meter={} obis={} using default 900s", meterSerial, profileObis);
        return 900; // or throw
    }

    @Override
    @Transactional
    public int refreshCapturePeriodSeconds(String meterSerial, String profileObis) {
        int cp = readCapturePeriodFromMeter(meterSerial, profileObis);
        if (cp > 0) {
            upsertCapturePeriod(meterSerial, profileObis, cp);
            putCache(meterSerial, profileObis, cp);
            return cp;
        }
        return -1;
    }

    /* ---------------- Internal helpers ---------------- */

    private int readCapturePeriodFromMeter(String serial, String profileObis) {
        try {
            GXDLMSClient client = sessionManager.getOrCreateClient(serial);
            if (client == null) {
                log.warn("No DLMS client available for meter {}", serial);
                return -1;
            }
            GXDLMSProfileGeneric profile = new GXDLMSProfileGeneric();
            profile.setLogicalName(profileObis);

            // Step 1: Read Capture Objects (Attribute 3)
            byte[][] captureRequest1 = client.read(profile, 3);
            GXReplyData captureReply1 =  dlmsReaderUtils.readDataBlock(client, serial, captureRequest1[0]);
            client.updateValue(profile, 3, captureReply1.getValue());

            // 🔎 Read attribute 7: current entries in use
            byte[][] captureRequest2 = client.read(profile, 7);
            GXReplyData captureReply2 =  dlmsReaderUtils.readDataBlock(client, serial, captureRequest2[0]);
            client.updateValue(profile, 7, captureReply2.getValue());
            int bufferCount = profile.getEntriesInUse(); // entriesInUse = attribute 7
            log.info("📦 Meter entries in use (attribute 7) = {}", bufferCount);

            // 🔎 Read attribute 4: max buffer size
            byte[][] bufferSize = client.read(profile, 4);
            GXReplyData bufferSizeReply =  dlmsReaderUtils.readDataBlock(client, serial, bufferSize[0]);
            client.updateValue(profile, 4, bufferSizeReply.getValue());
            int bufferCapacity = profile.getProfileEntries();
            log.info("🧮 Meter buffer capacity (attribute 4) = {}", bufferCapacity);

            // 🔎 Read attribute 8: capture period in seconds (optional)
            byte[][] captureSize = client.read(profile, 8);
            GXReplyData captureSizeReply = dlmsReaderUtils.readDataBlock(client, serial, captureSize[0]);
            client.updateValue(profile, 8, captureSizeReply.getValue());
            Long cp = profile.getCapturePeriod();

            if (captureSize == null || captureSize.length == 0) {
                log.warn("Empty capture period request frames meter={} obis={}", serial, profileObis);
                return -1;
            }

            int cpInt = cp == null ? -1 : cp.intValue();

            // 0 = monthly profile → use 1 month (represented as "1")
            if (cpInt == 0 && profileObis.startsWith("0.0.98")) {
                log.info("CapturePeriod=0 detected for monthly profile → assuming 1 month");
                return 1;  // 1 month / Day
            }
            log.info("Read capturePeriod from meter={} obis={} cp={}s", serial, profileObis, cpInt);
            return cpInt;

        } catch (Exception e) {
            log.error("Failed to read capture period meter={} obis={} msg={}", serial, profileObis, e.getMessage());
            return -1;
        }
    }

    @Transactional
    protected void upsertCapturePeriod(String meterSerial, String profileObis, int cp) {
        int updated = stateRepo.updateCapturePeriod(meterSerial, profileObis, cp);
        if (updated == 0) {
            // fallback insert
            MeterProfileState state = new MeterProfileState();
            state.setMeterSerial(meterSerial);
            state.setProfileObis(profileObis);
            state.setCapturePeriodSec(cp);
            state.setUpdatedAt(java.time.LocalDateTime.now());
            stateRepo.save(state);
        }
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

    private String cacheKey(String meterSerial, String obis) {
        return meterSerial + "::" + obis;
    }
}

