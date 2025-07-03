package com.memmcol.hes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.memmcol.hes.model.*;
import com.memmcol.hes.repository.DlmsObisObjectRepository;
import gurux.dlms.*;
import gurux.dlms.enums.Authentication;
import gurux.dlms.enums.InterfaceType;
import gurux.dlms.enums.ObjectType;
import gurux.dlms.enums.Unit;
import gurux.dlms.internal.GXCommon;
import gurux.dlms.objects.*;
import io.netty.handler.timeout.ReadTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

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

    public DlmsService(SessionManager sessionManager, DlmsObisObjectRepository repository, ProfileMetadataCacheService metadataCache) {
        this.sessionManager = sessionManager;
        this.repository = repository;
        this.metadataCache = metadataCache;
    }

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
        byte[] response = RequestResponseService.sendCommand(serial, aarq[0]);
        GXByteBuffer reply = new GXByteBuffer(response);

        //3. Parse AARE Response from Meter
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
        byte[] responseClock = RequestResponseService.sendCommand(serial, readClockRequest[0]);

        boolean hasData = dlmsClient.getData(responseClock, replyClock, null);

        if (!hasData || replyClock.getValue() == null) {
            throw new IllegalStateException("❌ Failed to parse clock data or data is null");
        }

        Object result = dlmsClient.updateValue(clock, 2, replyClock.getValue());  // ✅ Use replyClock.getValue()

        if (result instanceof GXDateTime dt) {
            clockDateTime = dt;
//            log.info("🕒 Meter Clock: {}", dt.toFormatString());
        } else if (result instanceof byte[] array) {
            clockDateTime = GXCommon.getDateTime(array);
        } else {
            throw new IllegalArgumentException("❌ Unexpected clock result type: " + result.getClass());
        }

        //6. Generate Disconnect Frame
        byte[] disconnectFrame = dlmsClient.disconnectRequest();
        if (disconnectFrame != null && disconnectFrame.length > 0) {
            log.info("📤 Disconnect Frame: {}", GXCommon.toHex(disconnectFrame));
            byte[] disconnectResponse = RequestResponseService.sendCommand(serial, disconnectFrame);

            // Some meters return nothing on disconnect, avoid NullPointerException
            if (disconnectResponse != null && disconnectResponse.length > 0) {
                log.info("📥 Disconnect Response: {}", GXCommon.toHex(disconnectResponse));
            } else {
                log.warn("⚠️ No response received from meter on disconnect. This may be normal.");
            }
        } else {
            log.warn("⚠️ Disconnect frame was empty or null — not sent.");
        }

//        Send this to close the association cleanly.

        // Convert to LocalDateTime
        LocalDateTime localDateTime = clockDateTime.getMeterCalendar().toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        // Format to "YYYY-MM-DD HH:MM:SS"
        strclock = localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        strclock = "🕒 Meter Clock for " + serial + ": " + strclock;

        log.info("🕒 Meter Clock: {}", strclock);

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
            byte[] response = RequestResponseService.sendCommand(meterSerial, scalerUnitRequest[0]);
            if (isAssociationLost(response)) {
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
                    readScalerUnit(client, meterSerial, reg, 3);
//                    readScaler(GXDLMSClient client, String serial, GXDLMSObject obj, int index)
                    scaler = reg.getScaler();
                    if (scaler == 0) {
                        scaler = 1.0;
                    }
                    unit = reg.getUnit();
//                    unit = reg.getUnit() != null ? reg.getUnit().toString() : "";

                    // Read value
                    result = readAttribute(client, meterSerial, reg, attributeIndex);
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
                    readScalerUnit(client, meterSerial, dr, 4);
                    scaler = dr.getScaler();
                    if (scaler == 0) {
                        scaler = 1.0;
                    }
                    unit = dr.getUnit();

                    // Read value
                    result = readAttribute(client, meterSerial, dr, attributeIndex);
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
                    Object raw = readAttribute(client, meterSerial, clk, attributeIndex);
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

                case DATA -> {
                    GXDLMSData data = new GXDLMSData();
                    data.setLogicalName(obisCode);
                    result = readAttribute(client, meterSerial, data, attributeIndex);
                    object = data;
                    unit = null;
                }

                default -> {
                    object = GXDLMSClient.createObject(type);
                    object.setLogicalName(obisCode);
                    result = readAttribute(client, meterSerial, object, attributeIndex);
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

    public List<String> getProfileCaptureColumns(GXDLMSClient client, String serial, String obisCode) {
        List<String> columns = new ArrayList<>();

        try {
            GXDLMSProfileGeneric profile = new GXDLMSProfileGeneric();
            profile.setLogicalName(obisCode);

            // Read capture objects (attribute 3)
            byte[][] request = client.read(profile, 3);
            byte[] response = RequestResponseService.sendCommand(serial, request[0]);

            if (isAssociationLost(response)) {
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

    private Object readAttribute(GXDLMSClient client, String serial, GXDLMSObject obj, int index) throws Exception {
        byte[][] request = client.read(obj, index);
        byte[] response = RequestResponseService.sendCommand(serial, request[0]);
        if (isAssociationLost(response)) {
            sessionManager.removeSession(serial);
            throw new AssociationLostException();
        }
        GXReplyData reply = new GXReplyData();
        client.getData(response, reply, null);
        return client.updateValue(obj, index, reply.getValue());
    }

    private Object readAttributeWithBlock(GXDLMSClient client, String serial, GXDLMSObject obj, int index) throws Exception {
        byte[][] request = client.read(obj, index);
        GXReplyData reply = readDataBlock(client, serial, request[0]);
        return client.updateValue(obj, index, reply.getValue());
    }

    private void readScalerUnit(GXDLMSClient client, String serial, GXDLMSObject obj, int index) throws Exception {
        byte[][] scalerUnitRequest = client.read(obj, index);
        byte[] response = RequestResponseService.sendCommand(serial, scalerUnitRequest[0]);
        if (isAssociationLost(response)) {
            sessionManager.removeSession(serial);
            throw new AssociationLostException();
        }
        GXReplyData reply = new GXReplyData();
        client.getData(response, reply, null);
        client.updateValue(obj, index, reply.getValue());
    }

    private double readScaler(GXDLMSClient client, String serial, GXDLMSObject obj, int index) throws Exception {
        byte[][] scalerUnitRequest = client.read(obj, index);
        byte[] response = RequestResponseService.sendCommand(serial, scalerUnitRequest[0]);
        if (isAssociationLost(response)) {
            sessionManager.removeSession(serial);
            throw new AssociationLostException();
        }
        GXReplyData reply = new GXReplyData();
        client.getData(response, reply, null);
        Object updateValue = client.updateValue(obj, index, reply.getValue());

        try {
            if (obj instanceof GXDLMSRegister reg) {
                double scaler = reg.getScaler();
                return BigDecimal.valueOf(Math.pow(10, scaler)).doubleValue();
            } else if (obj instanceof GXDLMSDemandRegister reg) {
                double scaler = reg.getScaler();
                return BigDecimal.valueOf(Math.pow(10, scaler)).doubleValue();
            } else {
                log.warn("⚠️ Object is not Register or Demand Register: {}", obj.getLogicalName());
            }
        } catch (Exception e) {
            log.error("❌ Error reading scaler from object {}: {}", obj.getLogicalName(), e.getMessage());
        }

        return BigDecimal.ONE.doubleValue();
    }

    /**
     * Reads a DLMS data block, including segmented/multi-frame responses.
     *
     * @param client       DLMS client for protocol interaction
     * @param serial       Meter serial number (used to route channel/command)
     * @param firstRequest Initial byte request (e.g., from `client.read`)
     * @return GXReplyData containing the full response
     */
    private GXReplyData readDataBlock(GXDLMSClient client, String serial, byte[] firstRequest) throws Exception {
        GXReplyData reply = new GXReplyData();

        try {


            // Send initial request
            byte[] response = RequestResponseService.sendCommand(serial, firstRequest);
            if (isAssociationLost(response)) {
                throw new AssociationLostException("Association lost with " + serial);
            }
            client.getData(response, reply, null);

            // Handle multi-block responses
            // Loop if there is more data
            while (reply.isMoreData()) {
                byte[] nextRequest;

                if (reply.isStreaming()) {
                    log.debug("Streaming block: no receiverReady needed.");
                    nextRequest = null; // Streaming continues automatically // Streaming doesn't need new request
                } else {
                    log.debug("Sending receiverReady...");
//                nextRequest = client.receiverReady(reply.getMoreData());
                    nextRequest = client.receiverReady(reply); // ✅ Correct

                }

                if (nextRequest == null) break; // Safety

                response = RequestResponseService.sendCommand(serial, nextRequest);

                if (isAssociationLost(response)) {
                    sessionManager.removeSession(serial);
                    throw new AssociationLostException("Association lost in block read");
                }

                client.getData(response, reply, null);
            }

        } catch (Exception e) {
            //
        }

        return reply;
    }


    /**
     * ✅ Step-by-Step Upgrade: readDataBlockWithPartialSupport
     * Reads a DLMS data block, including segmented/multi-frame responses.
     * Accumulate Partial Data from Profile Generic
     * Enhanced readDataBlockWithPartialSupport
     *
     * @param client         DLMS client for protocol interaction
     * @param serial         Meter serial number (used to route channel/command)
     * @param initialRequest Initial byte request (e.g., from `client.read`)
     * @return GXReplyData containing the full response
     */

    public List<ProfileRowDTO> readDataBlockWithPartialSupport(
            GXDLMSClient client,
            String serial,
            byte[] initialRequest,
            List<ProfileMetadataDTO.ColumnDTO> columns,
            int entryStart
    ) throws Exception {
        GXReplyData reply = new GXReplyData();

        ProfileRowParser parser = new ProfileRowParser();
        int entryId = entryStart;
        int blockcounter = 0;

        // 🟢 Initial request
        byte[] response = RequestResponseService.sendCommand(serial, initialRequest);

        if (isAssociationLost(response)) {
            sessionManager.removeSession(serial);
            throw new AssociationLostException("🔌 Association lost on first request.");
        }

        client.getData(response, reply, null);

        // ✅ Decode initial data
        List<ProfileRowDTO> parsed = parser.parse(columns, reply.getData(), entryId);
//        result.addAll(parsed);
        List<ProfileRowDTO> result = new ArrayList<>(parsed);
        entryId += parsed.size();

        // 🔁 Multi-block handling
        while (reply.isMoreData()) {
            byte[] nextRequest = client.receiverReady(reply);
            if (nextRequest == null) break;

            int retries = 3;
            while (retries-- > 0) {
                try {
                    response = RequestResponseService.sendCommand(serial, nextRequest);
                    break;
                } catch (ReadTimeoutException e) {
                    log.warn("⏱ Timeout reading block, retrying... ({})", retries);
                    if (retries == 0) throw e;
                } catch (Exception e) {
                    log.warn("⚠️ Partial read aborted. Parsed {} rows so far.", result.size());
                    return result;
                }
            }

            if (isAssociationLost(response)) {
                sessionManager.removeSession(serial);
                throw new AssociationLostException("Association lost during multi-block read.");
            }

            client.getData(response, reply, null);

            log.info("📦 Block #{} | Current Total Rows: {}", blockcounter++, result.size());

            // ✅ Parse current block
            List<ProfileRowDTO> moreParsed = parser.parse(columns, reply.getData(), entryId);
            result.addAll(moreParsed);
            entryId += moreParsed.size();
        }

        return result;

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
        GXReplyData reply = readDataBlock(client, meterSerial, request[0]);

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

    private boolean isAssociationLost(byte[] response) {
        // Match DLMS "Association Lost" signature
        if (response == null || response.length < 3) return false;
        // Check specific sequence or code e.g., ends with D8 01 01
        int len = response.length;
        return response[len - 3] == (byte) 0xD8
                && response[len - 2] == 0x01
                && response[len - 1] == 0x01;
    }

    private String getUnitSymbol(Unit unit) {
        return switch (unit) {
            case VOLTAGE -> "V";
            case CURRENT -> "A";
            case ACTIVE_ENERGY -> "kWh";
            case APPARENT_POWER -> "kVA";
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

    public List<ProfileRowDTO> readProfileData(GXDLMSClient client, String meterSerial, String obisCode, int entryCount) throws Exception {
        GXDLMSProfileGeneric profile = new GXDLMSProfileGeneric();
        profile.setLogicalName(obisCode); // e.g., 1.0.99.2.0.255

        // Step 1: Read Capture Objects (Attribute 3)
        byte[][] captureRequest = client.read(profile, 3);
        GXReplyData captureReply = readDataBlock(client, meterSerial, captureRequest[0]);
        client.updateValue(profile, 3, captureReply.getValue());

        // Step 2: Read Entries in Use (Attribute 2)
        byte[][] entryRequest = client.read(profile, 2);
        GXReplyData entryReply = readDataBlock(client, meterSerial, entryRequest[0]);
        client.updateValue(profile, 2, entryReply.getValue());

        int totalEntries = profile.getEntriesInUse();
        int startIndex = Math.max(1, totalEntries - entryCount + 1);

        log.info("Meter {} - Reading from entry {} to {}", meterSerial, startIndex, totalEntries);

        // Step 3: Read profile buffer using readRowsByEntry
        byte[][] readRequest = client.readRowsByEntry(profile, startIndex, entryCount);
        GXReplyData reply = readDataBlock(client, meterSerial, readRequest[0]);
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

                    DlmsDateUtils.ParsedTimestamp parsed = DlmsDateUtils.parseTimestamp(obj, i);
                    assert parsed != null;
                    row.getValues().put("timestamp", parsed.formatted); // ✅ Add this
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
            GXReplyData captureReply = readDataBlock(client, serial, captureRequest[0]);
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

        List<ProfileRowDTO> parsedRows = readDataBlockWithPartialSupport(
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

    public ProfileMetadataDTO readAndCacheProfileMetadata(GXDLMSClient client, String serial, String obisCode) throws Exception {
        GXDLMSProfileGeneric profile = new GXDLMSProfileGeneric();
        profile.setLogicalName(obisCode);

        // Step 1: Read Attribute 3 (Capture Object definitions)
        byte[][] captureRequest = client.read(profile, 3);
        GXReplyData captureReply = readDataBlock(client, serial, captureRequest[0]);

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
            GXReplyData entryReply = readDataBlock(client, serial, entryRequest[0]);
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
                    DlmsDateUtils.ParsedTimestamp parsed = DlmsDateUtils.parseTimestamp(val, i);
                    assert parsed != null;
                    row.getValues().put("timestamp", parsed.formatted); // ✅ Add this
                } else {
                    row.getValues().put(col.getObis(), val);
                }
            }

            result.add(row);
        }

        return result;
    }


}
