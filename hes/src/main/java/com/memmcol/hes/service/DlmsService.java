package com.memmcol.hes.service;

import gurux.dlms.GXByteBuffer;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXDateTime;
import gurux.dlms.GXReplyData;
import gurux.dlms.enums.ObjectType;
import gurux.dlms.enums.Unit;
import gurux.dlms.internal.GXCommon;
import gurux.dlms.manufacturersettings.GXObisCode;
import gurux.dlms.objects.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
public class DlmsService {
    private final SessionManager sessionManager;

    public DlmsService(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public String readClock(String serial) throws InvalidAlgorithmParameterException,
            NoSuchPaddingException, IllegalBlockSizeException,
            NoSuchAlgorithmException, BadPaddingException,
            SignatureException, InvalidKeyException {

        GXDLMSClient dlmsClient = new GXDLMSClient();

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

        log.info("🕒 Meter Clock: {}", strclock);

        return strclock;
    }

    public String greet(String name) {
        return "Al-hamdulilah. My first springboot DLMS application!. You are welcome, " + name + ".";
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


    private void readScalerUnit(GXDLMSClient client, String serial, GXDLMSObject obj, int index) throws Exception {
        byte[][] scalerUnitRequest = client.read(obj, index);
        byte[] response = RequestResponseService.sendCommand(serial, scalerUnitRequest[0]);
        if (isAssociationLost(response)) {
            throw new AssociationLostException();
        }
        GXReplyData reply = new GXReplyData();
        client.getData(response, reply, null);
        client.updateValue(obj, index, reply.getValue());
    }

    private Object readAttribute(GXDLMSClient client, String serial, GXDLMSObject obj, int index) throws Exception {
        byte[][] request = client.read(obj, index);
        byte[] response = RequestResponseService.sendCommand(serial, request[0]);
        if (isAssociationLost(response)) {
            throw new AssociationLostException();
        }
        GXReplyData reply = new GXReplyData();
        client.getData(response, reply, null);
        return client.updateValue(obj, index, reply.getValue());
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
}
