package com.memmcol.hes.service;

import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.model.ModelProfileMetadata;
import com.memmcol.hes.nettyUtils.SessionManager;
import com.memmcol.hes.repository.ModelProfileMetadataRepository;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXReplyData;
import gurux.dlms.objects.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileMetadataService {

    private static final String CACHE = "profileMetadata";
    private final CacheManager cacheManager;                       // ← Caffeine
    private final ModelProfileMetadataRepository repo;             // ← JPA
    private final SessionManager sessionManager;
    private final DlmsReaderUtils dlmsReaderUtils;

    /**
     * Return metadata for a given meter model & profile OBIS.
     * • Cache  →  DB  →  Meter  (in that order)
     */
    public List<ModelProfileMetadata> getOrLoadMetadata(
            String meterModel,
            String profileObis,
            String sampleSerial    // serial of *one* online meter of this model
    ) {
        // ② Cache
        String key = meterModel + "::" + profileObis;
        List<ModelProfileMetadata> cached = Objects.requireNonNull(cacheManager.getCache(CACHE)).get(key, List.class);
        if (cached != null) {
            log.info("📗 Caffeine hit for {}", key);
            return cached;
        }

        // ② DB
        List<ModelProfileMetadata> dbRows =
                repo.findByMeterModelAndProfileObis(meterModel, profileObis);
        if (!dbRows.isEmpty()) {
            log.info("📙 Loaded {} rows from DB for {}", dbRows.size(), key);
            Objects.requireNonNull(cacheManager.getCache(CACHE)).put(key, dbRows);
            return dbRows;
        }

        // ③ Meter read (only once per model)
        log.info("📡 No cache/DB hit – reading metadata from meter {}", sampleSerial);
        List<ModelProfileMetadata> fresh = loadFromMeterAndPersist(sampleSerial, meterModel, profileObis);

        if (!fresh.isEmpty()) {
            Objects.requireNonNull(cacheManager.getCache(CACHE)).put(key, fresh);
        }
        return fresh;
    }

    /**
     * Reads attribute-3 of the ProfileGeneric *and* scaler/unit of each Register-type
     * capture object, then persists the list.
     *
     * @param sampleSerial  an online meter of this model (used once)
     */
    public List<ModelProfileMetadata> loadFromMeterAndPersist(
            String sampleSerial,
            String meterModel,
            String profileObis) {

        try {
            GXDLMSClient client = sessionManager.getOrCreateClient(sampleSerial);
            if (client == null) throw new IllegalStateException("DLMS session not available");

            GXDLMSProfileGeneric profile = new GXDLMSProfileGeneric();
            profile.setLogicalName(profileObis);

            // ── 1. Read capture-objects list ──────────────────────────────────────
            byte[][] req = client.read(profile, 3);
            GXReplyData rep = dlmsReaderUtils.readDataBlock(client, sampleSerial, req[0]);
            client.updateValue(profile, 3, rep.getValue());

            List<ModelProfileMetadata> rows = new ArrayList<>();

            for (var coEntry : profile.getCaptureObjects()) {
                GXDLMSObject obj = coEntry.getKey();
                GXDLMSCaptureObject co = coEntry.getValue();

                double scaler = 1.0;
                String unit = "N/A";

                // ── 2. If Register-like, read attribute-3 once to get scaler/unit
                if (obj instanceof GXDLMSRegister) {
                    dlmsReaderUtils.readScalerUnit(client, sampleSerial, obj, 3);
                    scaler = DlmsScalerUnitHelper.extractScaler(obj);
                    unit = DlmsScalerUnitHelper.extractUnit(obj);
                }

                if (obj instanceof GXDLMSExtendedRegister || obj instanceof GXDLMSDemandRegister) {
                    dlmsReaderUtils.readScalerUnit(client, sampleSerial, obj, 4);
                    scaler = DlmsScalerUnitHelper.extractScaler(obj);
                    unit = DlmsScalerUnitHelper.extractUnit(obj);
                }

                ModelProfileMetadata row = ModelProfileMetadata.builder()
                        .meterModel(meterModel)
                        .profileObis(profileObis)
                        .captureObis(obj.getLogicalName())
                        .classId(obj.getObjectType().getValue())
                        .attributeIndex(co.getAttributeIndex())
                        .scaler(scaler)
                        .unit(unit)
                        .build();

                rows.add(row);
            }

            repo.saveAll(rows);          // ③ Persist once
            log.info("💾 Saved {} metadata rows for model={} profile={}", rows.size(), meterModel, profileObis);
            return rows;


        } catch (AssociationLostException ex) {
            sessionManager.removeSession(sampleSerial);
            log.error("Association lost with meter number: {}", sampleSerial);
            return Collections.emptyList();
        } catch (Exception ex) {
            log.error("❌ Metadata load failed", ex);
            return Collections.emptyList();
        }
    }

}
