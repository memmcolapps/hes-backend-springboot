package com.memmcol.hes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.memmcol.hes.domain.limiters.LimiterHelper;
import com.memmcol.hes.model.*;
import com.memmcol.hes.nettyUtils.RequestResponseService;
import com.memmcol.hes.nettyUtils.SessionManager;
import com.memmcol.hes.repository.DlmsObisObjectRepository;
import com.memmcol.hes.repository.ProfileChannel2Repository;
import com.memmcol.hes.trackByTimestamp.*;
import com.memmcol.hes.trackLastEntryRead.ProfileProgressTracker;
import gurux.dlms.*;
import gurux.dlms.enums.Authentication;
import gurux.dlms.enums.InterfaceType;
import gurux.dlms.enums.ObjectType;
import gurux.dlms.enums.Unit;
import gurux.dlms.internal.GXCommon;
import gurux.dlms.objects.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DlmsService {
    private final SessionManager sessionManager;
    private final DlmsObisObjectRepository repository;
    private final ProfileMetadataCacheService metadataCache;
    private final ProfileMetadataService profileMetadataService;
    private final ProfileChannel2Repository profileChannel2Repository;
    private final ProfileProgressTracker profileProgressTracker;
    private final ProfileTimestampTracker profileTimestampTracker;
    private final MeterProfileTimestampProgressRepository meterProfileTimestampProgressRepository;
    private final ProfileTimestampCacheService cacheService;
    private final MeterProfileStateService stateService;
    private final ProfileTimestampResolver profileTimestampResolver;
    private final MeterReadAdapter readAdapter;
    private final RequestResponseService requestResponseService;
    private final LimiterHelper limiterHelper;

    public DlmsService(SessionManager sessionManager,
                       DlmsObisObjectRepository repository,
                       ProfileMetadataCacheService metadataCache,
                       @Lazy ProfileMetadataService profileMetadataService,
                       ProfileChannel2Repository profileChannel2Repository,
                       ProfileProgressTracker profileProgressTracker,
                       ProfileTimestampTracker profileTimestampTracker,
                       MeterProfileTimestampProgressRepository meterProfileTimestampProgressRepository,
                       ProfileTimestampCacheService cacheService, MeterProfileStateService stateService,
                       ProfileTimestampResolver profileTimestampResolver,
                       MeterProfileStateRepository meterProfileStateRepository,
                       MeterReadAdapter readAdapter,
                       RequestResponseService requestResponseService, LimiterHelper limiterHelper) {
        this.sessionManager = sessionManager;
        this.repository = repository;
        this.metadataCache = metadataCache;
        this.profileMetadataService = profileMetadataService;
        this.profileChannel2Repository = profileChannel2Repository;
        this.profileProgressTracker = profileProgressTracker;
        this.profileTimestampTracker = profileTimestampTracker;
        this.meterProfileTimestampProgressRepository = meterProfileTimestampProgressRepository;
        this.cacheService = cacheService;
        this.stateService = stateService;
        this.profileTimestampResolver = profileTimestampResolver;
        this.readAdapter = readAdapter;
        this.requestResponseService = requestResponseService;
        this.limiterHelper = limiterHelper;
    }

    public static final DateTimeFormatter GLOBAL_TS_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public String readClock(String serial) throws InvalidAlgorithmParameterException,
            NoSuchPaddingException, IllegalBlockSizeException,
            NoSuchAlgorithmException, BadPaddingException,
            SignatureException, InvalidKeyException {

        GXDLMSClient dlmsClient = new GXDLMSClient(
                true,                    // Logical name referencing ✅
                1,                       // Client address (usually 1 for public)
                1,                       // Server address
                Authentication.LOW,     // Auth type
                "12345678",              // Password
                InterfaceType.WRAPPER    // DLMS WRAPPER mode
        );

        //2. Generate AARQ Frame
        byte[][] aarq = dlmsClient.aarqRequest();
        log.info("AARQ (hex): {}", GXCommon.toHex(aarq[0]));

        //Send to meter
        byte[] response = requestResponseService.sendCommand(serial, aarq[0]);
        GXByteBuffer reply = new GXByteBuffer(response);

        //3. Parse AARE Response from MetersEntity
        log.debug("Parsing AARE response: {}", GXCommon.toHex(response));

// 1. Strip off the 8-byte wrapper header
        byte[] payload = Arrays.copyOfRange(response, 8, response.length);

// 2. Create a GXByteBuffer from the actual AARE payload
        GXByteBuffer replyBuffer = new GXByteBuffer(payload);

// 3. Parse AARE response using the client instance (not statically)
        try {
            dlmsClient.parseAareResponse(replyBuffer); // This validates acceptance
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ AARE parse failed: {}", e.getMessage());
            log.info("Assuming meter accepted AARQ based on external check");
            // Optional: set association manually
//            dlmsClient.getSettings().setConnected(2);
        }
        log.info("🔓 Session Established: OK");

        //4. Build GET Frame for OBIS Clock (`0.0.1.0.0.255`)
        GXDLMSClock clock = new GXDLMSClock("0.0.1.0.0.255");
        // Attribute 2 = time
        byte[][] readClockRequest = dlmsClient.read(clock, 2);
        //Generate Clock frame
        for (byte[] frame : readClockRequest) {
            log.info("GET Clock Frame: {}", GXCommon.toHex(frame));
        }

        //5. Parse Clock GET.response
        GXReplyData replyClock = new GXReplyData();
        String strclock;
        GXDateTime clockDateTime = new GXDateTime();
        byte[] responseClock = requestResponseService.sendCommand(serial, readClockRequest[0]);

        boolean hasData = dlmsClient.getData(responseClock, replyClock, null);

        if (!hasData || replyClock.getValue() == null) {
            throw new IllegalStateException("❌ Failed to parse clock data or data is null");
        }

        Object result = dlmsClient.updateValue(clock, 2, replyClock.getValue());  // ✅ Use replyClock.getValue()

        if (result instanceof GXDateTime dt) {
            clockDateTime = dt;
//            log.info("🕒 MetersEntity Clock: {}", dt.toFormatString());
        } else if (result instanceof byte[] array) {
            clockDateTime = GXCommon.getDateTime(array);
        } else {
            throw new IllegalArgumentException("❌ Unexpected clock result type: " + result.getClass());
        }

        //   Send this to close the association cleanly.
        //6. Generate Disconnect Frame
        byte[] disconnectFrame = dlmsClient.disconnectRequest();
        if (disconnectFrame != null && disconnectFrame.length > 0) {
            log.info("📤 Disconnect Frame: {}", GXCommon.toHex(disconnectFrame));
            byte[] disconnectResponse = requestResponseService.sendCommand(serial, disconnectFrame);

            // Some meters return nothing on disconnect, avoid NullPointerException
            if (disconnectResponse != null && disconnectResponse.length > 0) {
                log.info("📥 Disconnect Response: {}", GXCommon.toHex(disconnectResponse));
            } else {
                log.warn("⚠️ No response received from meter on disconnect. This may be normal.");
            }
        } else {
            log.warn("⚠️ Disconnect frame was empty or null — not sent.");
        }

        // Convert to LocalDateTime
        LocalDateTime localDateTime = clockDateTime.getMeterCalendar().toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        // Format to "YYYY-MM-DD HH:MM:SS"
        strclock = localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        strclock = "🕒 Meter Clock for " + serial + ": " + strclock;

        log.info("🕒 Meters Clock: {}", strclock);

        return strclock;
    }

    public String greet(String name) {
        return "Al-hamdulilah. My first springboot DLMS application!. You are welcome, " + name + ".";
    }

    public ResponseEntity<Map<String, Object>> readScalerValue(String meterSerial, String obis) throws Exception {
        try {
            String[] parts = obis.split(";");
            if (parts.length != 4) {
                throw new IllegalArgumentException("OBIS format must be: classId;obisCode;attributeIndex;dataIndex");
            }

            int classId = Integer.parseInt(parts[0]);
            String obisCode = parts[1].trim();
            int attributeIndex = Integer.parseInt(parts[2]);
            int dataIndex = Integer.parseInt(parts[3]);

            GXDLMSClient client = sessionManager.getClient(meterSerial);
            if (client == null) {
                log.warn("No active session for {}. Attempting to create...", meterSerial);
                sessionManager.addSession(meterSerial, MeterConnections.getChannel(meterSerial));

                // Try again after attempting to establish session
                client = sessionManager.getClient(meterSerial);

                if (client == null) {
                    log.warn("No active session for {}", meterSerial);
                    Map<String, Object> error = new HashMap<>();
                    error.put("error", "No active session for meter: " + meterSerial);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                            Map.of(
                                    "error", "No active session",
                                    "serial", meterSerial,
                                    "tip", "Please establish association before reading OBIS"
                            )
                    );
                }
            }

            ObjectType type = ObjectType.forValue(classId);
            GXDLMSObject obj = new GXDLMSObject();
            double scaler = 1.0;
            int index = 2;

            if (Objects.requireNonNull(type) == ObjectType.REGISTER) {
                GXDLMSRegister reg = new GXDLMSRegister();
                reg.setLogicalName(obisCode);
                index = 3;
                obj = reg;
            } else if (Objects.requireNonNull(type) == ObjectType.DEMAND_REGISTER) {
                GXDLMSDemandRegister dr = new GXDLMSDemandRegister();
                dr.setLogicalName(obisCode);
                obj = dr;
                index = 4;
            } else {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Object is not Register or Demand Register");
                error.put("details", "Object is not Register or Demand Register");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }

            byte[][] scalerUnitRequest = client.read(obj, index);
            byte[] response = requestResponseService.sendCommand(meterSerial, scalerUnitRequest[0]);
            if (readAdapter.isAssociationLost(response)) {
                throw new AssociationLostException();
            }
            GXReplyData reply = new GXReplyData();
            client.getData(response, reply, null);
            Object updateValue = client.updateValue(obj, index, reply.getValue());

            if (obj instanceof GXDLMSRegister reg) {
                scaler = reg.getScaler();
                scaler = BigDecimal.valueOf(Math.pow(10, scaler)).doubleValue();
            } else if (obj instanceof GXDLMSDemandRegister reg) {
                scaler = reg.getScaler();
                scaler = BigDecimal.valueOf(Math.pow(10, scaler)).doubleValue();
            }

            Map<String, Object> obis_response = new LinkedHashMap<>();
            obis_response.put("Meter No", meterSerial);
            obis_response.put("obisCode", obisCode);
            obis_response.put("attributeIndex", attributeIndex);
            obis_response.put("dataIndex", dataIndex);
            obis_response.put("scaler", scaler);
            return ResponseEntity.ok(obis_response);
        } catch (AssociationLostException ex) {
            sessionManager.removeSession(meterSerial);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "🔄 Association lost with meter number: " + meterSerial);
            error.put("details", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "❌ Failed to read from OBIS or Error reading scaler from object");
            error.put("details", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }


    public ResponseEntity<Map<String, Object>> readObisValue(String meterSerial, String obis) {
        try {
            String[] parts = obis.split(";");
            if (parts.length != 4) {
                throw new IllegalArgumentException("OBIS format must be: classId;obisCode;attributeIndex;dataIndex");
            }

            int classId = Integer.parseInt(parts[0]);
            String obisCode = parts[1].trim();
            int attributeIndex = Integer.parseInt(parts[2]);


            int dataIndex = Integer.parseInt(parts[3]);

            GXDLMSClient client = sessionManager.getClient(meterSerial);
            if (client == null) {
                log.warn("No active session for {}. Attempting to create...", meterSerial);
                sessionManager.addSession(meterSerial, MeterConnections.getChannel(meterSerial));

                // Try again after attempting to establish session
                client = sessionManager.getClient(meterSerial);

                if (client == null) {
                    log.warn("No active session for {}", meterSerial);
                    Map<String, Object> error = new HashMap<>();
                    error.put("error", "No active session for meter: " + meterSerial);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                            Map.of(
                                    "error", "No active session",
                                    "serial", meterSerial,
                                    "tip", "Please establish association before reading OBIS"
                            )
                    );
                }
            }

            ObjectType type = ObjectType.forValue(classId);
            GXDLMSObject object;
            double scaler = 1.0;
            Unit unit;
            Object result;

            switch (type) {
                case REGISTER -> {
                    GXDLMSRegister reg = new GXDLMSRegister();
                    reg.setLogicalName(obisCode);

                    // Read Scaler+Unit first
                    readAdapter.readScalerUnit(client, meterSerial, reg, 3);
//                    readScaler(GXDLMSClient client, String serial, GXDLMSObject obj, int index)
                    scaler = reg.getScaler();
                    if (scaler == 0) {
                        scaler = 1.0;
                    }
                    unit = reg.getUnit();
//                    unit = reg.getUnit() != null ? reg.getUnit().toString() : "";

                    // Read value
                    result = readAdapter.readAttribute(client, meterSerial, reg, attributeIndex);
                    if (result instanceof Number) {
                        result = BigDecimal.valueOf(((Number) result).doubleValue())
                                .setScale(2, RoundingMode.HALF_UP)
                                .doubleValue();
                    }
                    object = reg;
                }

                case DEMAND_REGISTER -> {
                    GXDLMSDemandRegister dr = new GXDLMSDemandRegister();
                    dr.setLogicalName(obisCode);

                    // Read Scaler+Unit
                    readAdapter.readScalerUnit(client, meterSerial, dr, 4);
                    scaler = dr.getScaler();
                    if (scaler == 0) {
                        scaler = 1.0;
                    }
                    unit = dr.getUnit();

                    // Read value
                    result = readAdapter.readAttribute(client, meterSerial, dr, attributeIndex);
                    if (result instanceof Number) {
                        result = BigDecimal.valueOf(((Number) result).doubleValue())
                                .setScale(2, RoundingMode.HALF_UP)
                                .doubleValue();
                    }
                    object = dr;
                }

                case CLOCK -> {
                    GXDLMSClock clk = new GXDLMSClock();
                    clk.setLogicalName(obisCode);
                    GXDateTime clockDateTime;
                    Object raw = readAdapter.readAttribute(client, meterSerial, clk, attributeIndex);
                    if (raw instanceof GXDateTime dt) {
                        clockDateTime = dt;
                    } else if (raw instanceof byte[] array) {
                        clockDateTime = GXCommon.getDateTime(array);
                    } else {
                        throw new IllegalArgumentException("❌ Unexpected clock result type: " + raw.getClass());
                    }

                    // Convert to LocalDateTime
                    LocalDateTime localDateTime = clockDateTime.getMeterCalendar().toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime();

                    // Format to "YYYY-MM-DD HH:MM:SS"
                    result = localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

                    object = clk;
                    unit = null;
                }

                case LIMITER -> {
                    GXDLMSLimiter limiter = new GXDLMSLimiter();
                    limiter.setLogicalName(obisCode);
                    LimiterHelper.Threshold active = limiterHelper.getScaledThresholdWithUnit(limiter, "active", client, meterSerial, attributeIndex);
                    result = active.getActualValue();
                    scaler = active.getScaledValue();
                    unit = active.getUnit();
                    object = limiter;
                }

                case DATA -> {
                    GXDLMSData data = new GXDLMSData();
                    data.setLogicalName(obisCode);
                    result = readAdapter.readAttribute(client, meterSerial, data, attributeIndex);
                    object = data;
                    unit = null;
                }

                default -> {
                    object = GXDLMSClient.createObject(type);
                    object.setLogicalName(obisCode);
                    result = readAdapter.readAttribute(client, meterSerial, object, attributeIndex);
                    unit = null;
                }
//                default -> throw new IllegalArgumentException("Unsupported object type: " + type);
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("Meter No", meterSerial);
            response.put("obisCode", obisCode);
            response.put("attributeIndex", attributeIndex);
            response.put("dataIndex", dataIndex);
            response.put("value", result);
            response.put("scaler", scaler);
            if (unit != null) {
                response.put("unit", getUnitSymbol(unit)); // your mapping function
            }
            return ResponseEntity.ok(response);
        } catch (AssociationLostException ex) {
            sessionManager.removeSession(meterSerial);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "🔄 Association lost with meter number: " + meterSerial);
            error.put("details", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "❌ Failed to read from OBIS");
            error.put("details", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    //Read capture column
    public List<String> getProfileCaptureColumns(GXDLMSClient client, String serial, String obisCode) {
        List<String> columns = new ArrayList<>();

        try {
            GXDLMSProfileGeneric profile = new GXDLMSProfileGeneric();
            profile.setLogicalName(obisCode);

            // Read capture objects (attribute 3)
            byte[][] request = client.read(profile, 3);
            byte[] response = requestResponseService.sendCommand(serial, request[0]);

            if (readAdapter.isAssociationLost(response)) {
                sessionManager.removeSession(serial);
                getProfileCaptureColumns(client, serial, obisCode);
                throw new AssociationLostException();
            }

            GXReplyData reply = new GXReplyData();
            client.getData(response, reply, null);
            client.updateValue(profile, 3, reply.getValue());

            for (Map.Entry<GXDLMSObject, GXDLMSCaptureObject> entry : profile.getCaptureObjects()) {
                GXDLMSObject capturedObject = entry.getKey();
                if (capturedObject != null) {
                    columns.add(capturedObject.getLogicalName());
                }
            }
        } catch (Exception e) {
            log.error("❌ Error reading capture columns from {}: {}", obisCode, e.getMessage());
        }
        return columns;
    }


    /*
    Create a method to read the Association View using your DlmsBlockReader
        You are reading the Association Logical Name (LN) object:
        OBIS: 0.0.40.0.0.255
        ClassId: 15 (ASSOCIATION_LOGICAL_NAME)
        Attribute Index: 2 (Object List)
     */
    public List<GXDLMSObject> readAssociationObjects(String meterSerial) throws Exception {
        GXDLMSClient client = sessionManager.getOrCreateClient(meterSerial);
        if (client == null) {
            throw new IllegalStateException("No session found for meter: " + meterSerial);
        }

        GXDLMSAssociationLogicalName association = new GXDLMSAssociationLogicalName();
        association.setLogicalName("0.0.40.0.0.255"); // Standard OBIS for Association LN

        // Build request to read Object List (attribute index 2)
        byte[][] request = client.read(association, 2);

        // Read using block reader
        GXReplyData reply = readAdapter.readDataBlock(client, meterSerial, request[0]);

        // Update value in association object
        client.updateValue(association, 2, reply.getValue());

        client.getObjects().clear();
        client.getObjects().addAll(association.getObjectList());

//        for (GXDLMSObject obj : association.getObjectList()) {
//            client.getObjects().add(obj);
//        }

        processAssociationView(client);

        // Return the object list from the association
        return association.getObjectList();
    }

    public void processAssociationView(GXDLMSClient client) throws Exception {
        log.info("Processing association view");
        List<GXDLMSObject> objects = client.getObjects();

        // 1. Map to DTO
        List<ObisObjectDTO> dtos = objects.stream()
                .map(DlmsObisMapper::map)
                .collect(Collectors.toList());

        // 2. Save to JSON
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        try {
            String json = mapper.writeValueAsString(dtos);
            log.info("📦 OBIS Association View as JSON:\n{}", json);
        } catch (Exception e) {
            log.error("❌ Failed to convert OBIS DTOs to JSON", e);
        }

        mapper.writeValue(new File("dlms_association.json"), dtos);

        // 3. Save to PostgreSQL
        List<DlmsObisObjectEntity> entities = dtos.stream()
                .map(DlmsObisMapper::toEntity)
                .collect(Collectors.toList());
        repository.saveAll(entities);
    }


    static String getUnitSymbol(Unit unit) {
        return switch (unit) {
            case VOLTAGE -> "V";
            case CURRENT -> "A";
            case ACTIVE_POWER -> "kW";
            case APPARENT_POWER -> "kVA";
            case REACTIVE_POWER -> "kVar";
            case ACTIVE_ENERGY -> "kWh";
            case APPARENT_ENERGY -> "kVAh";
            case REACTIVE_ENERGY -> "kvarh";
            case FREQUENCY -> "Hz";
            // Add more cases as needed
            default -> unit.name(); // fallback to enum name
        };
    }

    public List<ProfileRowDTO> readProfileData(String meterSerial, String obisCode) throws Exception {
        GXDLMSClient client = sessionManager.getOrCreateClient(meterSerial);
        if (client == null) {
            throw new IllegalStateException("No session found for meter: " + meterSerial);
        }
        int entryCount = 10;
        return readProfileData(client, meterSerial, obisCode, entryCount);
    }

    //Pointer to readimg profile data
    public List<ProfileRowDTO> readProfileData(GXDLMSClient client, String meterSerial, String obisCode, int entryCount) throws Exception {
        GXDLMSProfileGeneric profile = new GXDLMSProfileGeneric();
        profile.setLogicalName(obisCode); // e.g., 1.0.99.2.0.255

        // Step 1: Read Capture Objects (Attribute 3)
        byte[][] captureRequest = client.read(profile, 3);
        GXReplyData captureReply = readAdapter.readDataBlock(client, meterSerial, captureRequest[0]);
        client.updateValue(profile, 3, captureReply.getValue());

        // Step 2: Read Entries in Use (Attribute 2)
        byte[][] entryRequest = client.read(profile, 2);
        GXReplyData entryReply = readAdapter.readDataBlock(client, meterSerial, entryRequest[0]);
        client.updateValue(profile, 2, entryReply.getValue());

        int totalEntries = profile.getEntriesInUse();
        int startIndex = Math.max(1, totalEntries - entryCount + 1);

        log.info("Meters {} - Reading from entry {} to {}", meterSerial, startIndex, totalEntries);

        // Step 3: Read profile buffer using readRowsByEntry
        byte[][] readRequest = client.readRowsByEntry(profile, startIndex, entryCount);
        GXReplyData reply = readAdapter.readDataBlock(client, meterSerial, readRequest[0]);
        client.updateValue(profile, 4, reply.getValue());

        // Step 4: Parse buffer
        List<ProfileRowDTO> rows = new ArrayList<>();

        List<Object> buffer = List.of(profile.getBuffer());
        List<Map.Entry<GXDLMSObject, GXDLMSCaptureObject>> columns = profile.getCaptureObjects();

        for (Object rowObj : buffer) {
            ProfileRowDTO row = new ProfileRowDTO();

            GXStructure structure = (GXStructure) rowObj;

            for (int i = 0; i < columns.size(); i++) {
                GXDLMSObject obj = columns.get(i).getKey();
                Object raw = structure.get(i); // ✅ Correct access


                String name = obj.getLogicalName();

                if (obj instanceof GXDLMSClock) {
                    GXDateTime time = (raw instanceof GXDateTime dt)
                            ? dt
                            : GXCommon.getDateTime((byte[]) raw);

                    LocalDateTime parsed = DlmsDateUtils.parseTimestampLdt(obj);
                    assert parsed != null;
                    row.getValues().put("timestamp", parsed); // ✅ Add this
                } else {
                    row.getValues().put(name, raw);
                }
            }
            rows.add(row);
        }
        return rows;
    }

    public List<ProfileRowDTO> readProfileBuffer(String serial, String obisCode) throws Exception {
        GXDLMSClient client = sessionManager.getOrCreateClient(serial);
        if (client == null) throw new IllegalStateException("No DLMS session found.");

        GXDLMSProfileGeneric profile = new GXDLMSProfileGeneric();
        profile.setLogicalName(obisCode);

        // Load cache
        Optional<ProfileMetadataDTO> cached = metadataCache.get(serial, obisCode);
        ProfileMetadataDTO metadata;
        List<ProfileMetadataDTO.ColumnDTO> columns;

        int knownEntryIndex = 1;
        if (cached.isPresent()) {
            log.info("♻️ Using cached metadata for meter: {} OBIS: {}", serial, obisCode);
            metadata = cached.get();
            columns = metadata.getColumns();
            knownEntryIndex = metadata.getEntriesInUse() + 1; // 👈 continue from last seen

            /*
            //To be revisited after testing
            //Aim is to cache the read capture objects (Attribute 3)
            List<Object> rawData = reply.getData(); // from readDataBlockWithPartialSupport
            List<Map.Entry<GXDLMSObject, GXDLMSCaptureObject>> captureColumns = applyMetadataToProfile(profile, metadata);
            List<ProfileRowDTO> parsed = ProfileCaptureObjectParser.parse(rawData, captureColumns);
            */

        } else {
            // Step 1: Read capture objects (Attribute 3)
            byte[][] captureRequest = client.read(profile, 3);
            GXReplyData captureReply = readAdapter.readDataBlock(client, serial, captureRequest[0]);
            client.updateValue(profile, 3, captureReply.getValue());

            // Convert capture objects to ColumnDTO list
            columns = new ArrayList<>();
            for (Map.Entry<GXDLMSObject, GXDLMSCaptureObject> entry : profile.getCaptureObjects()) {
                ProfileMetadataDTO.ColumnDTO col = new ProfileMetadataDTO.ColumnDTO();
                col.setObis(entry.getKey().getLogicalName());
                col.setClassId(entry.getKey().getObjectType().getValue());
                col.setAttributeIndex(2); // Fixed value, Gurux 4.0.79 doesn't expose getIndex()
                columns.add(col);
            }

            // Step 2: Save metadata to cache
            metadata = new ProfileMetadataDTO();
            metadata.setColumns(columns);
            metadata.setEntriesInUse(0); // will update after read
            metadataCache.put(serial, obisCode, metadata);
        }

        // Step 3: Read using readRowsByEntry
        int safeBatchSize = 50;
        byte[][] readRequest = client.readRowsByEntry(profile, knownEntryIndex, safeBatchSize);

        List<ProfileRowDTO> parsedRows = readAdapter.readDataBlockWithPartialSupport(
                client,
                serial,
                readRequest[0],
                columns,
                knownEntryIndex
        );

        // Step 4: Update metadata cache with new entry index
        if (!parsedRows.isEmpty()) {
            int lastEntryRead = parsedRows.get(parsedRows.size() - 1).getEntryId();
            metadata.setEntriesInUse(lastEntryRead);
            metadataCache.put(serial, obisCode, metadata);
        }

        savePartialToJson(parsedRows);
        return parsedRows;
    }

    //enhanced method, now reading attributes 7, 8, and 4, and handling rollover intelligently
    public void readAndSaveProfileChannel2(String serial, String model, int count) throws Exception {
        String profileObis = "1.0.99.2.0.255";

        int startIndex = profileProgressTracker.getLastRead(serial, profileObis);

        int nextIndex = startIndex + 1;

        //Read profile columns from cache -> DB -> MetersEntity
        //List<ModelProfileMetadata>
        List<ModelProfileMetadata> metadataList = profileMetadataService.getOrLoadMetadata(model, profileObis, serial);

        ProfileMetadataDTO metadataDTO = ProfileMetadataMapper.map(nextIndex, metadataList);

        // profile columns OBIS mapping
        Map<String, Double> scalers = (Map<String, Double>) metadataList.stream()
                .collect(Collectors.toMap(ModelProfileMetadata::getCaptureObis, ModelProfileMetadata::getScaler));
        log.debug("scalers: {}", scalers.toString());

        GXDLMSClient client = sessionManager.getOrCreateClient(serial);
        if (client == null) throw new IllegalStateException("DLMS session missing");

        GXDLMSProfileGeneric profile = new GXDLMSProfileGeneric();
        profile.setLogicalName(profileObis);
        // ✅ Automatically populate capture objects from metadata
        DlmsUtils.populateCaptureObjects(profile, metadataList);

        // 🔎 Read attribute 7: current entries in use
        byte[][] captureRequest = client.read(profile, 7);
        GXReplyData captureReply = readAdapter.readDataBlock(client, serial, captureRequest[0]);
        client.updateValue(profile, 7, captureReply.getValue());
        int bufferCount = profile.getEntriesInUse(); // entriesInUse = attribute 7
        log.info("📦 MetersEntity entries in use (attribute 7) = {}", bufferCount);

        // 🔎 Read attribute 4: max buffer size
        byte[][] bufferSize = client.read(profile, 4);
        GXReplyData bufferSizeReply = readAdapter.readDataBlock(client, serial, bufferSize[0]);
        client.updateValue(profile, 4, bufferSizeReply.getValue());
        int bufferCapacity = profile.getProfileEntries();
        log.info("🧮 MetersEntity buffer capacity (attribute 4) = {}", bufferCapacity);

        // 🔎 Read attribute 8: capture period in seconds (optional)
        byte[][] captureSize = client.read(profile, 8);
        GXReplyData captureSizeReply = readAdapter.readDataBlock(client, serial, captureSize[0]);
        client.updateValue(profile, 8, captureSizeReply.getValue());
        Long capturePeriod = profile.getCapturePeriod();
        log.info("⏱️ Capture period (attribute 8) = {} seconds", capturePeriod);

        // 🧠 Rollover Detection Logic
        if (bufferCount == 0) {
            log.warn("⚠️ MetersEntity buffer is empty — no data to read.");
            return;
        }

        if (nextIndex > bufferCount) {
            // Likely meter rollover
            log.warn("🔄 MetersEntity rollover detected for {}, startIndex {} > bufferCount {}. Resetting to 1.",
                    serial, nextIndex, bufferCount);

            nextIndex = 1;
            profileProgressTracker.updateLastRead(serial, profileObis, 1);
        }

        int endIndex = bufferCount;

        byte[][] readRequest = client.readRowsByEntry(profile, nextIndex, count); // batch read

        List<ProfileRowDTO> parsedRows = readAdapter.readDataBlockWithPartialSupport(
                client,
                serial,
                readRequest[0],
                metadataDTO.getColumns(),
                nextIndex
        );

//        savePartialToJsonChannel2(parsedRows);

        List<ProfileChannel2ReadingDTO> dtos = parsedRows.stream()
                .map(row -> {
                    Map<String, Object> valueMap = row.getValues();
                    List<Object> values = new ArrayList<>(valueMap.values());

                    return ProfileChannel2ReadingDTO.builder()
                            .meterSerial(serial)
                            .modelNumber(model)
                            .entryIndex(row.getEntryId()) // use explicit entry ID
                            .entryTimestamp(parseTimestamp(values.get(0)))  // safe parser
                            .exportActiveEnergy(safeParseDouble(values.get(1), "1.0.2.8.0.255", scalers))
                            .importActiveEnergy(safeParseDouble(values.get(2), "1.0.1.8.0.255", scalers))
                            .rawData(row.getRawData().toString())
                            .receivedAt(LocalDateTime.now())
                            .build();
                }).toList();

        //Check for duplicates
        try {
            List<Integer> entryIndexes = dtos.stream()
                    .map(ProfileChannel2ReadingDTO::getEntryIndex)
                    .toList();

            List<LocalDateTime> incomingTimestamps = dtos.stream()
                    .map(ProfileChannel2ReadingDTO::getEntryTimestamp)
                    .toList();

            List<Long> existingIndexes = profileChannel2Repository
                    .findExistingIndexesWithTimestamps(serial, entryIndexes, incomingTimestamps);

            // Filter out duplicates
            List<ProfileChannel2ReadingDTO> newDtos = dtos.stream()
                    .filter(dto -> !existingIndexes.contains(dto.getEntryIndex()))
                    .toList();

            if (newDtos.isEmpty()) {
                log.info("✅ All records already exist for meter {} – no new data to insert.", serial);
                return;
            }

            // Convert and save new entities
            List<ProfileChannel2Reading> entities2 = ProfileChannel2ReadingMapper.toEntityList(newDtos);
            profileChannel2Repository.saveAll(entities2);

            int newLast = newDtos.stream()
                    .mapToInt(ProfileChannel2ReadingDTO::getEntryIndex)
                    .max()
                    .orElse(nextIndex);

            profileProgressTracker.updateLastRead(serial, profileObis, newLast);

        } catch (DataIntegrityViolationException e) {
            log.error("❌ Data integrity violation during save: {}", e.getMessage(), e);
            throw e;  // rethrow or handle gracefully
        }
    }

    public int readProfileChannel2ByTimestamp(String serial, String model, LocalDateTime endDate) throws Exception {
        String profileObis = "1.0.99.2.0.255";

//        // ⏱️ Determine the timestamp to resume from
//        LocalDateTime resumeFrom = (startDate != null)
//                ? profileTimestampTracker.getLastTimestamp(serial, profileObis)
//                : startDate;
//        log.info("📅 Reading meter {} from timestamp: {}", serial, resumeFrom);

        LocalDateTime resumeFrom = profileTimestampTracker.getLastTimestamp(serial, profileObis);
        log.info("📅 Reading meter {} from timestamp: {}", serial, resumeFrom);

        LocalDateTime resumeAfter = resumeFrom.plusHours(4);

        // 🔧 Load metadata and setup DLMS profile object
        List<ModelProfileMetadata> metadataList = profileMetadataService.getOrLoadMetadata(model, profileObis, serial);
        ProfileMetadataDTO metadataDTO = ProfileMetadataMapper.map(1, metadataList);

        GXDLMSClient client = sessionManager.getOrCreateClient(serial);
        if (client == null) throw new IllegalStateException("DLMS session missing");

        GXDLMSProfileGeneric profile = new GXDLMSProfileGeneric();
        profile.setLogicalName(profileObis);
        DlmsUtils.populateCaptureObjects(profile, metadataList);

        // 🔎 Read attribute 8: capture period in seconds (optional)
        byte[][] captureSize = client.read(profile, 8);
        GXReplyData captureSizeReply = readAdapter.readDataBlock(client, serial, captureSize[0]);
        client.updateValue(profile, 8, captureSizeReply.getValue());
        Long capturePeriod = profile.getCapturePeriod();
        log.info("⏱️ Capture period (attribute 8) = {} seconds", capturePeriod);

        // 📨 Read rows from the meter using date range
        byte[][] readRequest = client.readRowsByRange(profile, DlmsUtils.toGXDateTime(resumeFrom), DlmsUtils.toGXDateTime(resumeAfter)); // null = up to latest

        List<ProfileRowDTO> parsedRows = readAdapter.readDataBlockWithPartialSupport(
                client,
                serial,
                readRequest[0],
                metadataDTO.getColumns(),
                1
        );

        if (parsedRows.isEmpty()) {
            log.info("📭 No new profile rows found for meter {} since {}", serial, resumeFrom);
            return 0;
        }

        // 🔧 Parse scalers from metadata
        Map<String, Double> scalers = metadataList.stream()
                .collect(Collectors.toMap(ModelProfileMetadata::getCaptureObis, ModelProfileMetadata::getScaler));

        // 🎯 Map parsed rows into DTOs
        List<ProfileChannel2ReadingDTO> dtos = parsedRows.stream()
                .map(row -> {
                    Map<String, Object> valueMap = row.getValues();
                    List<Object> values = new ArrayList<>(valueMap.values());

                    return ProfileChannel2ReadingDTO.builder()
                            .meterSerial(serial)
                            .modelNumber(model)
                            .entryIndex(row.getEntryId()) // retained for logging/debug only
                            .entryTimestamp(parseTimestamp(values.get(0)))
                            .exportActiveEnergy(safeParseDouble(values.get(1), "1.0.2.8.0.255", scalers))
                            .importActiveEnergy(safeParseDouble(values.get(2), "1.0.1.8.0.255", scalers))
                            .rawData(row.getRawData().toString())
                            .receivedAt(LocalDateTime.now())
                            .build();
                }).toList();

        // 📌 Deduplication by timestamp only
        List<LocalDateTime> incomingTimestamps = dtos.stream()
                .map(ProfileChannel2ReadingDTO::getEntryTimestamp)
                .toList();

        List<LocalDateTime> existingTimestamps = profileChannel2Repository
                .findExistingTimestamps(serial, incomingTimestamps);

        List<ProfileChannel2ReadingDTO> newDtos = dtos.stream()
                .filter(dto -> !existingTimestamps.contains(dto.getEntryTimestamp()))
                .toList();

        if (newDtos.isEmpty()) {
            log.info("✅ No new readings to save — all timestamps already exist.");
            return 0;
        }

        // 💾 Save new readings
        List<ProfileChannel2Reading> entities = ProfileChannel2ReadingMapper.toEntityList(newDtos);
        profileChannel2Repository.saveAll(entities);

        // ⏱️ Update timestamp tracker
        LocalDateTime newLastTimestamp = newDtos.stream()
                .map(ProfileChannel2ReadingDTO::getEntryTimestamp)
                .max(LocalDateTime::compareTo)
                .orElse(resumeFrom);

        profileTimestampTracker.updateLastTimestamp(serial, profileObis, newLastTimestamp);

        log.info("📌 Profile progress for {} updated to lastTimestamp = {}", serial, newLastTimestamp);

        return entities.size(); // return number of saved records
    }

    //Next profile by date range (Using Gurux)
    public int readProfileChannel2ByTimestampDatablock(String serial, String model, LocalDateTime endDate) throws Exception {
        String profileObis = "1.0.99.2.0.255";

//        // ⏱️ Determine the timestamp to resume from
//        LocalDateTime resumeFrom = (startDate != null)
//                ? profileTimestampTracker.getLastTimestamp(serial, profileObis)
//                : startDate;
//        log.info("📅 Reading meter {} from timestamp: {}", serial, resumeFrom);

        LocalDateTime resumeFrom = profileTimestampTracker.getLastTimestamp(serial, profileObis);
        log.info("📅 Reading meter {} from timestamp: {}", serial, resumeFrom);

        LocalDateTime resumeAfter = resumeFrom.plusHours(1);

        // 🔧 Load metadata and setup DLMS profile object
        List<ModelProfileMetadata> metadataList = profileMetadataService.getOrLoadMetadata(model, profileObis, serial);
        ProfileMetadataDTO metadataDTO = ProfileMetadataMapper.map(1, metadataList);

        GXDLMSClient client = sessionManager.getOrCreateClient(serial);
        if (client == null) throw new IllegalStateException("DLMS session missing");

        GXDLMSProfileGeneric profile = new GXDLMSProfileGeneric();
        profile.setLogicalName(profileObis);
        DlmsUtils.populateCaptureObjects(profile, metadataList);

        // 🔎 Read attribute 7: current entries in use
        byte[][] captureRequest = client.read(profile, 7);
        GXReplyData captureReply = readAdapter.readDataBlock(client, serial, captureRequest[0]);
        client.updateValue(profile, 7, captureReply.getValue());
        int bufferCount = profile.getEntriesInUse(); // entriesInUse = attribute 7
        log.info("📦 MetersEntity entries in use (attribute 7) = {}", bufferCount);

        // 🔎 Read attribute 4: max buffer size
        byte[][] bufferSize = client.read(profile, 4);
        GXReplyData bufferSizeReply = readAdapter.readDataBlock(client, serial, bufferSize[0]);
        client.updateValue(profile, 4, bufferSizeReply.getValue());
        int bufferCapacity = profile.getProfileEntries();
        log.info("🧮 MetersEntity buffer capacity (attribute 4) = {}", bufferCapacity);

        // 🔎 Read attribute 8: capture period in seconds (optional)
        byte[][] captureSize = client.read(profile, 8);
        GXReplyData captureSizeReply = readAdapter.readDataBlock(client, serial, captureSize[0]);
        client.updateValue(profile, 8, captureSizeReply.getValue());
        Long capturePeriod = profile.getCapturePeriod();
        log.info("⏱️ Capture period (attribute 8) = {} seconds", capturePeriod);

        // 📨 Read rows from the meter using date range
        byte[][] readRequest = client.readRowsByRange(profile, DlmsUtils.toGXDateTime(resumeFrom), DlmsUtils.toGXDateTime(resumeAfter)); // null = up to latest
        GXReplyData reply = readAdapter.readDataBlock(client, serial, readRequest[0]);
        client.updateValue(profile, 2, reply.getValue());

        // Step 4: Parse the buffer
        List<ProfileChannel2ReadingDTO> readings = new ArrayList<>();
        List<Object> buffer = List.of(profile.getBuffer());
        // 🔧 Parse scalers from metadata
        Map<String, Double> scalers = metadataList.stream()
                .collect(Collectors.toMap(ModelProfileMetadata::getCaptureObis, ModelProfileMetadata::getScaler));

        for (Object rowObj : buffer) {
            if (!(rowObj instanceof Object[] row)) {
                log.warn("Skipping unexpected buffer row type: {}", rowObj.getClass());
                continue;
            }

            LocalDateTime timestamp = null;
            Double activeEnergy = null;
            Double reactiveEnergy = null;

            // Parse timestamp
            if (row[0] instanceof GXDateTime gxDateTime) {
                timestamp = parseTimestamp(gxDateTime);
            }

            // Parse energies
            if (row[1] instanceof Number) {
                activeEnergy = safeParseDouble(((Number) row[1]).doubleValue(), "1.0.1.8.0.255", scalers);
                assert activeEnergy != null;
                activeEnergy = BigDecimal.valueOf(((Number) activeEnergy).doubleValue())
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
            }

            if (row[2] instanceof Number) {
                reactiveEnergy = safeParseDouble(((Number) row[2]).doubleValue(), "1.0.2.8.0.255", scalers);
                assert reactiveEnergy != null;
                reactiveEnergy = BigDecimal.valueOf(((Number) reactiveEnergy).doubleValue())
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
            }

            if (timestamp != null) {
                readings.add(ProfileChannel2ReadingDTO.builder()
                        .entryIndex(1)
                        .entryTimestamp(timestamp)
                        .importActiveEnergy(activeEnergy)
                        .exportActiveEnergy(reactiveEnergy)
                        .meterSerial(serial)
                        .modelNumber(model)
                        .receivedAt(LocalDateTime.now())
                        .build());
            }
        }

        // 📌 Deduplication by timestamp only
        List<LocalDateTime> incomingTimestamps = readings.stream()
                .map(ProfileChannel2ReadingDTO::getEntryTimestamp)
                .toList();

        List<LocalDateTime> existingTimestamps = profileChannel2Repository
                .findExistingTimestamps(serial, incomingTimestamps);

        List<ProfileChannel2ReadingDTO> newDtos = readings.stream()
                .filter(dto -> !existingTimestamps.contains(dto.getEntryTimestamp()))
                .toList();

        if (newDtos.isEmpty()) {
            log.info("✅ No new readings to save — all timestamps already exist.");
            return 0;
        }

        // 💾 Save new readings
        List<ProfileChannel2Reading> entities = ProfileChannel2ReadingMapper.toEntityList(newDtos);
        profileChannel2Repository.saveAll(entities);

        // ⏱️ Update timestamp tracker
        LocalDateTime newLastTimestamp = newDtos.stream()
                .map(ProfileChannel2ReadingDTO::getEntryTimestamp)
                .max(LocalDateTime::compareTo)
                .orElse(resumeFrom);

        profileTimestampTracker.updateLastTimestamp(serial, profileObis, newLastTimestamp);

        log.info("📌 Profile progress for {} updated to lastTimestamp = {}", serial, newLastTimestamp);

        return entities.size(); // return number of saved records
    }


    public void tryReadWithRetry(String serial, String model, int count) {
        int maxAttempts = 3;
        int delayMs = 5000;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                readAndSaveProfileChannel2(serial, model, count);
                return; // ✅ Success
            } catch (IOException | GXDLMSException e) {
                log.warn("⚠️ Attempt {}/{} failed for meter {}: {}", attempt, maxAttempts, serial, e.getMessage());

                if (attempt == maxAttempts) {
                    log.error("❌ All retry attempts failed for meter {} – skipping.", serial);
                } else {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("Retry interrupted for meter {}", serial);
                        return;
                    }
                }
            } catch (Exception e) {
                log.error("❌ Unexpected error for meter {}: {}", serial, e.getMessage(), e);
                return; // Stop retrying for unknown/unhandled exceptions
            }
        }
    }

    private Double scale(Map<String, Number> raw, String obis, Map<String, Double> scalers) {
        Number val = raw.get(obis);
        if (val == null) return null;
        double scaler = scalers.getOrDefault(obis, 1.0);
        return val.doubleValue() * scaler;
    }

    private Double safeParseDouble(Object val, String obis, Map<String, Double> scalers) {
        double result = 1.00;
        if (val instanceof Number) {
            double scaler = scalers.getOrDefault(obis, 1.0);
            result = ((Number) val).doubleValue() * scaler;
        } else
            try {
                result = Double.parseDouble(val.toString());
            } catch (Exception ex) {
                return null;
            }
        return result;
    }

    private Double safeParseDouble(Object val) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        try {
            return Double.parseDouble(val.toString());
        } catch (Exception ex) {
            return null;
        }
    }

    private LocalDateTime parseTimestamp(Object raw) {
        switch (raw) {
            case null -> {
                return null;
            }

            case GXDateTime gxdt -> {
                Date date = gxdt.getValue(); // returns java.util.Date
                return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            }

            case LocalDateTime dt -> {
                return dt;
            }
            case String str -> {
                try {
                    // Acceptable format in your JSON: 2024-08-21 10:30:00
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    return LocalDateTime.parse(str, formatter);
                } catch (Exception e) {
                    log.error("❌ Failed to parse timestamp: {}", str, e);
                }
            }
            default -> {
            }
        }

        return null;
    }


    public List<Map.Entry<GXDLMSObject, GXDLMSCaptureObject>> applyMetadataToProfile(GXDLMSProfileGeneric profile, ProfileMetadataDTO metadata) {
        List<Map.Entry<GXDLMSObject, GXDLMSCaptureObject>> columns = new ArrayList<>();

        for (ProfileMetadataDTO.ColumnDTO column : metadata.getColumns()) {
            GXDLMSObject obj = GuruxObjectFactory.create(column.getClassId(), column.getObis());

            // Set logical name — important for correct OBIS decoding
            obj.setLogicalName(column.getObis());

            GXDLMSCaptureObject co = new GXDLMSCaptureObject();
            co.setAttributeIndex(column.getAttributeIndex());

            columns.add(new AbstractMap.SimpleEntry<>(obj, co));
        }

        // ❗We CANNOT set into `profile` directly — so we return the list for use in parsing
        return columns;
    }


    public List<Map.Entry<GXDLMSObject, GXDLMSCaptureObject>> buildCaptureObjectsFromMetadata(ProfileMetadataDTO metadata) {
        List<Map.Entry<GXDLMSObject, GXDLMSCaptureObject>> result = new ArrayList<>();

        for (ProfileMetadataDTO.ColumnDTO column : metadata.getColumns()) {
            GXDLMSObject obj = GuruxObjectFactory.create(column.getClassId(), column.getObis());

            GXDLMSCaptureObject co = new GXDLMSCaptureObject();
            co.setAttributeIndex(column.getAttributeIndex());

            result.add(new AbstractMap.SimpleEntry<>(obj, co));
        }

        return result;
    }

//    💾 Sample Save Method (optional)

    private void savePartialToJson(List<ProfileRowDTO> rows) {
        try {
            ObjectMapper mapper = new ObjectMapper()
                    .registerModule(new JavaTimeModule())                          // ✅ Enables Java 8 date/time
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)       // ✅ Use ISO-8601, not numeric timestamps
                    .enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(new File("dlms_profile_partial.json"), rows);
            log.info("📝 Partial profile saved to dlms_profile_partial.json with {} rows", rows.size());
        } catch (IOException e) {
            log.warn("⚠️ Could not save partial profile data", e);
        }
    }

    private void savePartialToJsonChannel2(List<ProfileRowDTO> rows) {
        try {
            ObjectMapper mapper = new ObjectMapper()
                    .registerModule(new JavaTimeModule())                          // ✅ Enables Java 8 date/time
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)       // ✅ Use ISO-8601, not numeric timestamps
                    .enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(new File("dlms_profile_channel2.json"), rows);
            log.info("📝 Partial profile saved to dlms_profile_channel2.json with {} rows", rows.size());
        } catch (IOException e) {
            log.warn("⚠️ Could not save partial profile data", e);
        }
    }

    public ProfileMetadataDTO readAndCacheProfileMetadata(GXDLMSClient client, String serial, String obisCode) throws Exception {
        GXDLMSProfileGeneric profile = new GXDLMSProfileGeneric();
        profile.setLogicalName(obisCode);

        // Step 1: Read Attribute 3 (Capture Object definitions)
        byte[][] captureRequest = client.read(profile, 3);
        GXReplyData captureReply = readAdapter.readDataBlock(client, serial, captureRequest[0]);

        Object[] captureArray = (Object[]) captureReply.getValue(); // ⚠️ Attribute 3 is always an array of GXStructures
        ProfileMetadataDTO metadataDTO = new ProfileMetadataDTO();

        for (Object item : captureArray) {
            GXStructure struct = (GXStructure) item;

            int classId = ((Number) struct.get(0)).intValue();
            byte[] obisBytes = (byte[]) struct.get(1);
            int attributeIndex = ((Number) struct.get(2)).intValue();

            String obis = GXCommon.toLogicalName(obisBytes);

            ProfileMetadataDTO.ColumnDTO column = new ProfileMetadataDTO.ColumnDTO();
            column.setClassId(classId);
            column.setObis(obis);
            column.setAttributeIndex(attributeIndex);

            metadataDTO.getColumns().add(column);
        }
        // Step 2: Optionally read Attribute 2 (EntriesInUse)
        try {
            byte[][] entryRequest = client.read(profile, 2);
            GXReplyData entryReply = readAdapter.readDataBlock(client, serial, entryRequest[0]);
            client.updateValue(profile, 2, entryReply.getValue());
            metadataDTO.setEntriesInUse(profile.getEntriesInUse());
        } catch (Exception e) {
            log.warn("⚠️ Could not read EntriesInUse: {}", e.getMessage());
            metadataDTO.setEntriesInUse(-1); // Unknown
        }

        // Step 3: Cache the result
        metadataCache.put(serial, obisCode, metadataDTO);
        log.info("✅ Metadata cached for meter: {}, OBIS: {}", serial, obisCode);
        return metadataDTO;
    }


    public List<ProfileRowDTO> decodeProfileRowsFromReply(List<ProfileMetadataDTO.ColumnDTO> columns, Object replyValue) {
        List<ProfileRowDTO> result = new ArrayList<>();

        if (!(replyValue instanceof List<?> rows)) {
            log.warn("⚠️ Unexpected reply value format: {}", replyValue.getClass());
            return result;
        }

        for (Object rowObj : rows) {
            ProfileRowDTO row = new ProfileRowDTO();

            // Usually each row is a GXStructure (array of column values)
            GXStructure structure;
            try {
                structure = (GXStructure) rowObj;
            } catch (ClassCastException e) {
                log.warn("⚠️ Row is not a GXStructure: {}", rowObj.getClass());
                continue;
            }

            for (int i = 0; i < columns.size() && i < structure.size(); i++) {
                ProfileMetadataDTO.ColumnDTO col = columns.get(i);
                Object val = structure.get(i);

                if (col.getClassId() == 8) { // GXDLMSClock
                    GXDateTime dt = (val instanceof GXDateTime) ? (GXDateTime) val : GXCommon.getDateTime((byte[]) val);
                    LocalDateTime parsed = DlmsDateUtils.parseTimestampLdt(val);
                    assert parsed != null;
                    row.getValues().put("timestamp", parsed.format(GLOBAL_TS_FORMATTER)); // ✅ Add this
                } else {
                    row.getValues().put(col.getObis(), val);
                }
            }

            result.add(row);
        }

        return result;
    }

//    ✅ Step 1: Loader Method – Read Attribute 3 + Save Metadata
//    Create this method in a service (e.g. ProfileCaptureLoaderService) to be passed into the getMetadata(...) call.
    public List<ModelProfileMetadata> readAttribute3FromMeterAndConvertToEntity(
            String meterModel,    //Temporary replaced with meter serial number. The meter model will be added later,
            String profileObis
    ) {
        try {
            GXDLMSClient client = sessionManager.getOrCreateClient(meterModel);
            if (client == null) throw new IllegalStateException("No DLMS session found.");

            GXDLMSProfileGeneric profile = new GXDLMSProfileGeneric();
            profile.setLogicalName(profileObis);

            // Read attribute 3 (CaptureObjects)
            byte[][] request = client.read(profile, 3);
            GXReplyData reply = readAdapter.readDataBlock(client, meterModel, request[0]);
            client.updateValue(profile, 3, reply.getValue());

            List<ModelProfileMetadata> result = new ArrayList<>();
            List<Map.Entry<GXDLMSObject, GXDLMSCaptureObject>> captureObjects = profile.getCaptureObjects();

            for (Map.Entry<GXDLMSObject, GXDLMSCaptureObject> entry : captureObjects) {
                GXDLMSObject obj = entry.getKey();
                GXDLMSCaptureObject co = entry.getValue();

                String captureObis = obj.getLogicalName();
                int classId = obj.getObjectType().getValue();
                int attrIndex = co.getAttributeIndex();

                double scaler = DlmsScalerUnitHelper.extractScaler(obj);
                String unit = DlmsScalerUnitHelper.extractUnit(obj);

                ModelProfileMetadata meta = ModelProfileMetadata.builder()
                        .meterModel(meterModel)
                        .profileObis(profileObis)
                        .captureObis(captureObis)
                        .classId(classId)
                        .attributeIndex(attrIndex)
                        .scaler(scaler)
                        .unit(unit)
                        .build();

                result.add(meta);
            }

            return result;

        } catch (Exception ex) {
            log.warn("⚠️ Failed to read metadata from meter model={} obis={}", meterModel, profileObis, ex);
            return Collections.emptyList();
        }
    }

    public LocalDateTime getInitialTimestamp(String serial, String obisCode) throws Exception {
        GXDLMSClient client = sessionManager.getOrCreateClient(serial);
        if (client == null) throw new IllegalStateException("Session not established");

        GXDLMSProfileGeneric profile = new GXDLMSProfileGeneric();
        profile.setLogicalName(obisCode);

        // Step 1: Read Capture Objects (attribute 3)
        byte[][] captureRequest = client.read(profile, 3);
        GXReplyData captureReply = readAdapter.readDataBlock(client, serial, captureRequest[0]);
        client.updateValue(profile, 3, captureReply.getValue());

        // Step 2: Read entry #1 only
        byte[][] readRequest = client.readRowsByEntry(profile, 1, 1);
        GXReplyData reply = readAdapter.readDataBlock(client, serial, readRequest[0]);
        client.updateValue(profile, 2, reply.getValue());

        List<Object> buffer = List.of(profile.getBuffer());
        if (buffer.isEmpty()) return null;

        GXStructure structure = (GXStructure) buffer.get(0);
        Object tsRaw = structure.get(0);  // timestamp is typically first column

        return DlmsDateUtils.parseTimestampLdt(tsRaw);
    }

    @Transactional
    public void initializeProfileState(GXDLMSClient client, GXDLMSProfileGeneric profile, String serial) throws Exception {
        String obis = profile.getLogicalName();

        // ✅ Try cache first
        LocalDateTime cachedTs = cacheService.get(serial, obis);
        if (cachedTs != null) {
            log.info("Using cached timestamp: {} for {}", cachedTs, serial);
            return;
        }

        // ✅ Fetch first timestamp (first 10 entries)
        byte[][] readRequest = client.readRowsByEntry(profile, 1, 10);
        GXReplyData reply = readAdapter.readDataBlock(client, serial, readRequest[0]);
        client.updateValue(profile, 4, reply.getValue());

        List<?> rows = (List<?>) reply.getValue();
        if (rows == null || rows.isEmpty()) {
            log.warn("No profile rows found for meter {}", serial);
            return;
        }

        // ✅ Extract first timestamp
        Object firstVal = ((GXStructure) rows.get(0)).get(0);
        LocalDateTime initialTs = DlmsDateUtils.parseTimestampLdt(firstVal);

        // ✅ Fetch capture period
        // 🔎 Read attribute 8: capture period in seconds (optional)
        byte[][] captureSize = client.read(profile, 8);
        GXReplyData captureSizeReply = readAdapter.readDataBlock(client, serial, captureSize[0]);
        client.updateValue(profile, 8, captureSizeReply.getValue());
        Long capturePeriodSec = profile.getCapturePeriod();
        log.info("⏱️ Capture period (attribute 8) = {} seconds", capturePeriodSec);


        // ✅ Save to DB
        stateService.upsertTimestampAndCapturePeriod(serial, obis, initialTs, Math.toIntExact(capturePeriodSec));

        // ✅ Cache the timestamp
        cacheService.put(serial, obis, initialTs);

        log.info("Initial timestamp for {} [{}] = {}, capturePeriodSec={}", serial, obis, initialTs, capturePeriodSec);
    }


//    7. Putting It All Together in Your Read Service

    public void testBootstrapTimestamp(String serial, String model) throws Exception {
        String profileObis = "1.0.99.2.0.255";

        LocalDateTime startTs = profileTimestampResolver.resolveStartTimestamp(serial, profileObis, model);
        if (startTs == null) {
            log.warn("MetersEntity {} returned no profile data.", serial);
        } else {
            log.info("Resolved start timestamp for {} ({}) = {}", serial, profileObis, startTs);
        }
    }


    public void readProfileInBatchesByTimestamp(String serial,
                                                String profileObis,
                                                GXDLMSClient client,
                                                GXDLMSProfileGeneric profile,
                                                LocalDateTime startTs,
                                                int capturePeriodSec,
                                                int batchSize) throws Exception {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentTs = startTs;

        while (currentTs.isBefore(now)) {
            LocalDateTime endTs = currentTs.plusSeconds((long) capturePeriodSec * batchSize);

            GXDateTime fromGX = new GXDateTime(Date.from(currentTs.atZone(ZoneId.systemDefault()).toInstant()));
            GXDateTime toGX = new GXDateTime(Date.from(endTs.atZone(ZoneId.systemDefault()).toInstant()));

            log.info("Reading profile {} from {} to {}", profileObis, currentTs, endTs);

            // 📨 Read rows from the meter using date range
            byte[][] readRequest = client.readRowsByRange(profile, fromGX, toGX); // null = up to latest
            GXReplyData reply = readAdapter.readDataBlock(client, serial, readRequest[0]);
            client.updateValue(profile, 2, reply.getValue());

            // Apply result to profile buffer
            client.updateValue(profile, 2, reply.getValue());

//            List<List<Object>> rows = profile.getBuffer(); // decoded rows

//            if (rows == null || rows.isEmpty()) {
//                log.info("No more rows from {} to {}", currentTs, endTs);
//                break; // Stop reading
//            }

            // Persist batch (decode + save)
//            persistRows(rows, serial, profileObis);

            // Move currentTs forward
            currentTs = endTs;
        }
    }

}
