package com.memmcol.hes.service;

import com.memmcol.hes.domain.profile.ObisMappingService;
import com.memmcol.hes.domain.profile.ObisObjectType;
import com.memmcol.hes.exception.AssociationLostException;
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

import java.util.*;

import static com.memmcol.hes.nettyUtils.RequestResponseService.logTx;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileMetadataService {

    private static final String CACHE = "profileMetadata";
    private static final String EVENT_LOG_PREFIX = "0.0.99.98.";
    private final CacheManager cacheManager;                       // ← Caffeine
    private final ModelProfileMetadataRepository repo;             // ← JPA
    private final SessionManager sessionManager;
    private final DlmsReaderUtils dlmsReaderUtils;
    private final ObisMappingService obisMappingService;

    /**
     * Return metadata for a given meter model & profile OBIS.
     * • Cache  →  DB  →  MetersEntity  (in that order)
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
                repo.findByMeterModelAndProfileObisOrderByCaptureIndexAsc(meterModel, profileObis);
        if (!dbRows.isEmpty()) {
            log.info("📙 Loaded {} rows from DB for {}", dbRows.size(), key);
            Objects.requireNonNull(cacheManager.getCache(CACHE)).put(key, dbRows);
            return dbRows;
        }

        // ③ MetersEntity read (only once per model)
        log.info("📡 No cache/DB hit – reading metadata from meter {}", sampleSerial);
        List<ModelProfileMetadata> fresh = loadFromMeterAndPersist(sampleSerial, meterModel, profileObis);

        // If meter association fails (or any other reason), fall back for event logs.
        // Event logs are always 2 columns (datetime, event_code) per provided OBIS documentation.
        if (fresh.isEmpty() && isEventLogProfileObis(profileObis)) {
            log.warn("⚠️ Metadata could not be learned from meter. Falling back to default event-log capture objects for {}", key);
            fresh = buildDefaultEventLogMetadata(meterModel, profileObis);
            repo.saveAll(fresh);
        }

        if (!fresh.isEmpty()) {
            Objects.requireNonNull(cacheManager.getCache(CACHE)).put(key, fresh);
        }
        return fresh;
    }

    private static boolean isEventLogProfileObis(String profileObis) {
        if (profileObis == null) return false;
        return profileObis.startsWith(EVENT_LOG_PREFIX) && profileObis.endsWith(".255");
    }

    /**
     * Default capture objects for event logs:
     * - Clock (class 8, LN 0.0.1.0.0.255, attribute 2)
     * - Event code as Data (class 1, LN 0.0.96.11.0.255, attribute 2)
     *
     * Note: these are used only when the meter cannot be associated to learn capture objects (attr 3).
     */
    private static List<ModelProfileMetadata> buildDefaultEventLogMetadata(String meterModel, String profileObis) {
        ModelProfileMetadata ts = ModelProfileMetadata.builder()
                .meterModel(meterModel)
                .profileObis(profileObis)
                .captureObis("0.0.1.0.0.255")
                .classId(8)
                .attributeIndex(2)
                .scaler(1.0)
                .unit("N/A")
                .captureIndex(0)
                .columnName("event_time")
                .description("Event timestamp")
                .multiplyBy("CTPT")
                .type(ObisObjectType.NONE)
                .build();

        ModelProfileMetadata code = ModelProfileMetadata.builder()
                .meterModel(meterModel)
                .profileObis(profileObis)
                .captureObis("0.0.96.11.0.255")
                .classId(1)
                .attributeIndex(2)
                .scaler(1.0)
                .unit("N/A")
                .captureIndex(1)
                .columnName("event_code")
                .description("Event code")
                .multiplyBy("CTPT")
                .type(ObisObjectType.NONE)
                .build();

        return List.of(ts, code);
    }

    /**
     * Reads attribute-3 of the ProfileGeneric *and* scaler/unit of each Register-type
     * capture object, then persists the list.
     *
     * @param meterSerial  an online meter of this model (used once)
     */
    public List<ModelProfileMetadata> loadFromMeterAndPersist(
            String meterSerial,
            String meterModel,
            String profileObis) {

        try {
            String msg = String.format(
                    "Reading capture objects, scaler and units for model=%s meter=%s obis=%s",
                    meterModel, meterSerial, profileObis);
            log.info(msg);
            logTx(meterSerial, msg);

            GXDLMSClient client = sessionManager.getOrCreateClient(meterSerial);
            if (client == null) throw new IllegalStateException("DLMS session not available");

            GXDLMSProfileGeneric profile = new GXDLMSProfileGeneric();
            profile.setLogicalName(profileObis);

            // ── 1. Read capture-objects list ──────────────────────────────────────
            byte[][] req = client.read(profile, 3);
            GXReplyData rep = dlmsReaderUtils.readDataBlock(client, meterSerial, req[0]);
            client.updateValue(profile, 3, rep.getValue());

            List<ModelProfileMetadata> rows = new ArrayList<>();
            List<Map.Entry<GXDLMSObject, GXDLMSCaptureObject>> captureObjects = profile.getCaptureObjects();

            for (int i = 0; i < captureObjects.size(); i++) {
                var entry = captureObjects.get(i);
                GXDLMSObject obj = entry.getKey();
                GXDLMSCaptureObject co = entry.getValue();

                double scaler = 1.0;
                String unit = "N/A";

                // ── 2. If Register-like, read attribute-3 once to get scaler/unit
                if (obj instanceof GXDLMSRegister) {
                    dlmsReaderUtils.readScalerUnit(client, meterSerial, obj, 3);
                    scaler = DlmsScalerUnitHelper.extractScaler(obj);
                    unit = DlmsScalerUnitHelper.extractUnit(obj);
                }

                if (obj instanceof GXDLMSExtendedRegister || obj instanceof GXDLMSDemandRegister || obj instanceof GXDLMSLimiter) {
                    dlmsReaderUtils.readScalerUnit(client, meterSerial, obj, 3);
                    scaler = DlmsScalerUnitHelper.extractScaler(obj);
                    unit = DlmsScalerUnitHelper.extractUnit(obj);
                }

                int captureIndex = i; // Index in the capture object list
                String obis = obj.getLogicalName();
                String multiplyBy = "CTPT"; // can later be determined per meter model if needed
                ObisColumnDto dto = obisMappingService.getDescriptionAndColumnName(obis, meterModel);
                String descriptionName = dto.getDescription(); // e.g., "Voltage (V)"
                String columnName = dto.getColumnName();  // e.g., "voltage"

                ModelProfileMetadata row = ModelProfileMetadata.builder()
                        .meterModel(meterModel)
                        .profileObis(profileObis)
                        .captureObis(obis)
                        .classId(obj.getObjectType().getValue())
                        .attributeIndex(co.getAttributeIndex())
                        .scaler(scaler)
                        .unit(unit)
                        .captureIndex(captureIndex)
                        .columnName(columnName)
                        .description(descriptionName)
                        .multiplyBy(multiplyBy)
                        .type(ObisObjectType.NONE)
                        .build();

                rows.add(row);
            }

            repo.saveAll(rows);          // ③ Persist once
            log.info("💾 Saved {} metadata rows for model={} profile={}", rows.size(), meterModel, profileObis);
            return rows;


        } catch (AssociationLostException ex) {
            sessionManager.removeSession(meterSerial);
            log.error("Association lost with meter number: {}", meterSerial);
            return Collections.emptyList();
        } catch (Exception ex) {
            log.error("❌ Metadata load failed", ex);
            return Collections.emptyList();
        }
    }

}
