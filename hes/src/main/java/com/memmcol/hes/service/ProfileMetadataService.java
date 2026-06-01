package com.memmcol.hes.service;

import com.memmcol.hes.domain.events.HouseholdExtendedEventObis;
import com.memmcol.hes.domain.events.HouseholdTokenEventObis;
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

    @org.springframework.beans.factory.annotation.Value("${hes.metadata.refresh.enabled:true}")
    private boolean refreshEnabled;

    /**
     * Return metadata for a given meter model & profile OBIS.
     * • Cache  →  DB  →  MetersEntity  (in that order)
     */
    /**
     * Drops cache + DB rows for this model/profile and re-reads capture objects from the meter.
     */
    public List<ModelProfileMetadata> refreshFromMeter(
            String meterSerial,
            String meterModel,
            String profileObis) {
        String key = meterModel + "::" + profileObis;
        var cache = cacheManager.getCache(CACHE);
        if (cache != null) {
            cache.evict(key);
        }

        List<ModelProfileMetadata> existing =
                repo.findByMeterModelAndProfileObisOrderByCaptureIndexAsc(meterModel, profileObis);
        if (!existing.isEmpty()) {
            repo.deleteAll(existing);
            log.info("Deleted {} stale metadata row(s) for {} before meter refresh", existing.size(), key);
        }

        List<ModelProfileMetadata> fresh = loadFromMeterAndPersist(meterSerial, meterModel, profileObis);
        if (!fresh.isEmpty() && cache != null) {
            cache.put(key, fresh);
        }
        return fresh;
    }

    public List<ModelProfileMetadata> getOrLoadMetadata(
            String meterModel,
            String profileObis,
            String sampleSerial    // serial of *one* online meter of this model
    ) {
        // ① Cache
        String key = meterModel + "::" + profileObis;
        List<ModelProfileMetadata> cached = Objects.requireNonNull(cacheManager.getCache(CACHE)).get(key, List.class);
        if (cached != null) {
            log.info("📗 Caffeine hit for {}", key);
            if (!isBroken(cached)) {
                return cached;
            }
            log.warn("⚠️ Cached metadata for {} is broken, ignoring cache", key);
        }

        // ② DB
        List<ModelProfileMetadata> dbRows =
                repo.findByMeterModelAndProfileObisOrderByCaptureIndexAsc(meterModel, profileObis);

        if (!dbRows.isEmpty()) {
            if (isBroken(dbRows) && refreshEnabled) {
                log.warn("📙 Loaded broken metadata from DB for {}. Triggering refresh from meter {}", key, sampleSerial);
                // Clear the broken rows so we don't have duplicates or mixed versions
                repo.deleteAll(dbRows);
            } else {
                log.info("📙 Loaded {} rows from DB for {}", dbRows.size(), key);
                Objects.requireNonNull(cacheManager.getCache(CACHE)).put(key, dbRows);
                return dbRows;
            }
        }

        // ③ MetersEntity read (only once per model)
        log.info("📡 No cache/DB hit – reading metadata from meter {}", sampleSerial);
        List<ModelProfileMetadata> fresh = loadFromMeterAndPersist(sampleSerial, meterModel, profileObis);

        // If meter association fails (or any other reason), fall back for event logs.
        // Event logs are always 2 columns (datetime, event_code) per provided OBIS documentation.
        if (fresh.isEmpty() && isHouseholdTokenEventObis(profileObis)) {
            log.warn("⚠️ Metadata could not be learned from meter. Falling back to default household token-event capture objects for {}", key);
            fresh = buildDefaultHouseholdTokenEventMetadata(meterModel, profileObis);
            repo.saveAll(fresh);
        } else if (fresh.isEmpty() && isHouseholdExtendedEventObis(profileObis)) {
            log.warn("⚠️ Metadata could not be learned from meter. Falling back to default household fraud/control capture objects for {}", key);
            fresh = buildDefaultHouseholdExtendedEventMetadata(meterModel, profileObis);
            repo.saveAll(fresh);
        } else if (fresh.isEmpty() && isEventLogProfileObis(profileObis)) {
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

    private static boolean isHouseholdTokenEventObis(String profileObis) {
        return HouseholdTokenEventObis.isHouseholdTokenEvent(profileObis);
    }

    private static boolean isHouseholdExtendedEventObis(String profileObis) {
        return HouseholdExtendedEventObis.isHouseholdExtendedEvent(profileObis);
    }

    private boolean isBroken(List<ModelProfileMetadata> metadata) {
        if (metadata == null || metadata.isEmpty()) return false;

        // Condition 1: Any register/scaler with type NONE (Legacy issue)
        boolean hasNoneType = metadata.stream()
                .anyMatch(m -> (m.getClassId() == 3 || m.getClassId() == 4 || m.getClassId() == 5)
                        && (m.getType() == null || m.getType() == ObisObjectType.NONE));

        if (hasNoneType) return true;

        // Condition 2: Known misalignment for MMX-310-NG Channel 2 (0.2.24.3.0.255 is often used as a dummy or alias for Ch2 in some systems,
        // but based on prompt it's 0.2.24.3.0.255)
        // If current_l1 (index 2) comes before voltage_l2 (index 5), it's misaligned for MMX-310-NG
        if ("MMX-310-NG".equals(metadata.get(0).getMeterModel())) {
             Optional<ModelProfileMetadata> curL1 = metadata.stream().filter(m -> "current_l1".equals(m.getColumnName())).findFirst();
             Optional<ModelProfileMetadata> volL2 = metadata.stream().filter(m -> "voltage_l2".equals(m.getColumnName())).findFirst();
             if (curL1.isPresent() && volL2.isPresent() && curL1.get().getCaptureIndex() < volL2.get().getCaptureIndex()) {
                 return true;
             }
        }

        return false;
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
     * Four-column fallback for household recharge / management token logs (manufacturer spec).
     */
    private static List<ModelProfileMetadata> buildDefaultHouseholdTokenEventMetadata(String meterModel, String profileObis) {
        boolean recharge = HouseholdTokenEventObis.isRecharge(profileObis);
        List<ModelProfileMetadata> base = buildDefaultEventLogMetadata(meterModel, profileObis);
        List<ModelProfileMetadata> extended = new ArrayList<>(base);

        extended.add(ModelProfileMetadata.builder()
                .meterModel(meterModel)
                .profileObis(profileObis)
                .captureObis("0.0.96.14.0.255")
                .classId(1)
                .attributeIndex(2)
                .scaler(1.0)
                .unit("N/A")
                .captureIndex(2)
                .columnName(recharge ? "recharge_amount_kwh" : "manage_token_type")
                .description(recharge ? "Recharge amount kWh" : "Manage token type")
                .multiplyBy("CTPT")
                .type(ObisObjectType.NONE)
                .build());

        extended.add(ModelProfileMetadata.builder()
                .meterModel(meterModel)
                .profileObis(profileObis)
                .captureObis("0.0.96.15.0.255")
                .classId(1)
                .attributeIndex(2)
                .scaler(1.0)
                .unit("N/A")
                .captureIndex(3)
                .columnName(recharge ? "recharge_token" : "manage_token")
                .description(recharge ? "Recharge token" : "Manage token")
                .multiplyBy("CTPT")
                .type(ObisObjectType.NONE)
                .build());

        return extended;
    }

    /**
     * Fallback capture objects for household fraud (4 columns) and control (3 columns) event logs.
     */
    private static List<ModelProfileMetadata> buildDefaultHouseholdExtendedEventMetadata(String meterModel, String profileObis) {
        List<ModelProfileMetadata> base = buildDefaultEventLogMetadata(meterModel, profileObis);
        List<ModelProfileMetadata> extended = new ArrayList<>(base);

        if (HouseholdExtendedEventObis.isControl(profileObis)) {
            extended.add(ModelProfileMetadata.builder()
                    .meterModel(meterModel)
                    .profileObis(profileObis)
                    .captureObis("0.0.96.14.0.255")
                    .classId(1)
                    .attributeIndex(2)
                    .scaler(1.0)
                    .unit("N/A")
                    .captureIndex(2)
                    .columnName("reason_of_operation")
                    .description("Reason of operation")
                    .multiplyBy("CTPT")
                    .type(ObisObjectType.NONE)
                    .build());
            return extended;
        }

        extended.add(ModelProfileMetadata.builder()
                .meterModel(meterModel)
                .profileObis(profileObis)
                .captureObis("1.0.0.8.0.255")
                .classId(3)
                .attributeIndex(2)
                .scaler(1.0)
                .unit("kWh")
                .captureIndex(2)
                .columnName("total_absolute_active_kwh")
                .description("Total absolute active kWh")
                .multiplyBy("CTPT")
                .type(ObisObjectType.NONE)
                .build());

        extended.add(ModelProfileMetadata.builder()
                .meterModel(meterModel)
                .profileObis(profileObis)
                .captureObis("0.0.96.15.0.255")
                .classId(1)
                .attributeIndex(2)
                .scaler(1.0)
                .unit("kWh")
                .captureIndex(3)
                .columnName("balance_kwh")
                .description("Balance kWh")
                .multiplyBy("CTPT")
                .type(ObisObjectType.NONE)
                .build());

        return extended;
    }

    /**
     * Reads attribute-3 of the ProfileGeneric *and* scaler/unit of each Register-type
     * capture object, then persists the list.
     *
     * @param meterSerial  an online meter of this model (used once)
     */
    public List<ModelProfileMetadata> loadFromMeterAndPersistV1(
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
            // Attribute 3 of Profile Generic is the capture_objects list
            byte[][] req = client.read(profile, 3);
            GXReplyData rep = dlmsReaderUtils.readDataBlock(client, meterSerial, req[0]);

            // LOG: Raw data structure from the meter
            log.info("Meter {} Raw Capture Objects Data: {}", meterSerial, rep.getValue());

            client.updateValue(profile, 3, rep.getValue());

            List<ModelProfileMetadata> rows = new ArrayList<>();
            List<Map.Entry<GXDLMSObject, GXDLMSCaptureObject>> captureObjects = profile.getCaptureObjects();

            log.info("Meter {} has {} capture objects defined in profile {}",
                    meterSerial, captureObjects.size(), profileObis);

            for (int i = 0; i < captureObjects.size(); i++) {
                var entry = captureObjects.get(i);
                GXDLMSObject obj = entry.getKey();
                GXDLMSCaptureObject co = entry.getValue();

                double scaler = 1.0;
                String unit = "N/A";
                String obis = obj.getLogicalName();
                int classId = obj.getObjectType().getValue();

                ObisObjectType type = ObisObjectType.NON_SCALER;
                if (classId == 8) {
                    type = ObisObjectType.CLOCK;
                } else if (classId == 3 || classId == 4 || classId == 5 || classId == 71) {
                    type = ObisObjectType.SCALER;
                }

                // ── 2. Read Scaler/Unit for Register-type objects ───────────────────
                if (type == ObisObjectType.SCALER) {
                    try {
                        dlmsReaderUtils.readScalerUnit(client, meterSerial, obj, 3);
                        scaler = DlmsScalerUnitHelper.extractScaler(obj);
                        unit = DlmsScalerUnitHelper.extractUnit(obj);
                    } catch (Exception e) {
                        log.warn("Could not read scaler/unit for OBIS {} on meter {}", obis, meterSerial);
                    }

                    // Special handling for Phase Angle OBIS (1.0.81.7.x.255)
                    // If manufacturer omitted scaler, it usually means it should be 0.1 (multiplied by 10 internally)
                    if (obis.startsWith("1.0.81.7.") && (scaler == 1.0 || scaler == 0.0)) {
                        log.info("Applying manual 0.1 scaler for phase angle OBIS {}", obis);
                        scaler = 0.1;
                    }
                }

                // ── 3. Mapping and Metadata Construction ──────────────────────────
                int captureIndex = i;
                String multiplyBy = "CTPT";
                ObisColumnDto dto = obisMappingService.getDescriptionAndColumnName(obis, meterModel);

                log.info("Mapping Index [{}]: OBIS {} -> Column: {}, Unit: {}, Type: {}",
                        captureIndex, obis, dto.getColumnName(), unit, type);

                ModelProfileMetadata row = ModelProfileMetadata.builder()
                        .meterModel(meterModel)
                        .profileObis(profileObis)
                        .captureObis(obis)
                        .classId(classId)
                        .attributeIndex(co.getAttributeIndex())
                        .scaler(scaler)
                        .unit(unit)
                        .captureIndex(captureIndex)
                        .columnName(dto.getColumnName())
                        .description(dto.getDescription())
                        .multiplyBy(multiplyBy)
                        .type(type)
                        .build();

                rows.add(row);
            }

            // ── 4. Persist ────────────────────────────────────────────────────────
            repo.saveAll(rows);
            log.info("💾 Successfully persisted {} metadata rows for meter {}/profile {}",
                    rows.size(), meterSerial, profileObis);

            return rows;

        } catch (AssociationLostException ex) {
            sessionManager.removeSession(meterSerial);
            log.error("Association lost with meter number: {}", meterSerial);
            return Collections.emptyList();
        } catch (Exception ex) {
            log.error("❌ Metadata load failed for meter {}: {}", meterSerial, ex.getMessage(), ex);
            return Collections.emptyList();
        }
    }


}
