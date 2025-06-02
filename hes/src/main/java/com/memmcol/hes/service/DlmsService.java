package com.memmcol.hes.service;

import gurux.dlms.GXByteBuffer;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXDateTime;
import gurux.dlms.GXReplyData;
import gurux.dlms.enums.ObjectType;
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
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class DlmsService {
    private final GXDLMSClient dlmsClient;
    private final SessionManager sessionManager;


    public DlmsService(GXDLMSClient dlmsClient, SessionManager sessionManager) {
        this.dlmsClient = dlmsClient;
        this.sessionManager = sessionManager;
    }

    public String readClock(String serial) throws InvalidAlgorithmParameterException,
            NoSuchPaddingException, IllegalBlockSizeException,
            NoSuchAlgorithmException, BadPaddingException,
            SignatureException, InvalidKeyException {

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

    public String greet(String name){
        return "Al-hamdulilah. My first springboot DLMS application!. You are welcome, " + name + ".";
    }

    public ResponseEntity<Map<String, Object>> readObisValue(String serial, String obis) {
        try {
            String[] parts = obis.split(";");
            if (parts.length != 4) {
                throw new IllegalArgumentException("OBIS format must be: classId;obisCode;attributeIndex;dataIndex");
            }
            int classId = Integer.parseInt(parts[0]);
            String obisCode = parts[1].trim();
            int attributeIndex = Integer.parseInt(parts[2]);
            int dataIndex = Integer.parseInt(parts[3]);

            // Object name mapping (for known OBIS codes)
            Map<String, String> obisNameMap = Map.of(
                    "1.0.32.7.0.255", "Voltage on L1",
                    "1.0.31.7.0.255", "Current on L1"
            );
            String objectName = obisNameMap.getOrDefault(obisCode, "Unknown Object");

//            ObjectType type = ObjectType.forValue(classId);
//            GXDLMSObject object = switch (type) {
//                case REGISTER -> new GXDLMSRegister();
//                case CLOCK -> new GXDLMSClock();
//                case DATA -> new GXDLMSData();
//                case PROFILE_GENERIC -> new GXDLMSProfileGeneric();
//                default -> throw new IllegalArgumentException("Unsupported type");
//            };
//            object.setLogicalName(obisCode);

            GXDLMSRegister gxdlmsRegister = new GXDLMSRegister(obisCode);
            byte[][] readRequest = dlmsClient.read(gxdlmsRegister, attributeIndex);
            log.debug("Read request: {}", GXCommon.toHex(readRequest[0]));
            byte[] response = RequestResponseService.sendCommand(serial, readRequest[0]);

            log.debug("Response: {}", GXCommon.toHex(response));

            GXReplyData reply = new GXReplyData();
            dlmsClient.getData(response, reply, null);
            Object result = dlmsClient.updateValue(gxdlmsRegister, attributeIndex, reply.getValue());

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("serial", serial);
            responseBody.put("obisCode", obisCode);
            responseBody.put("attributeIndex", attributeIndex);
            responseBody.put("dataIndex", dataIndex);
            responseBody.put("objectName", objectName);
            responseBody.put("value", result);

            return ResponseEntity.ok(responseBody);

//            GXDLMSObject object1 = GXDLMS.createObject(new GXObisCode(obisCode), ObjectType.forValue(classId), 0, 0);
//            GXDLMSObject object = dlmsClient.createObject(type);

        } catch (Exception e){
            Map<String, Object> error = new HashMap<>();
            error.put("error", "❌ Failed to read from OBIS");
            error.put("details", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }

    }

    public ResponseEntity<?> readObis(String serial, String obis) throws InvalidAlgorithmParameterException,
            NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException,
            SignatureException, InvalidKeyException {
        Optional<MeterSession> optional = sessionManager.getSession(serial);
        if (optional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("❌ Meter not connected");
        }

        MeterSession session = optional.get();
        GXDLMSClient client = session.getClient();

        if (!session.isAssociated()) {
            byte[] aarq = dlmsClient.aarqRequest()[0];
            byte[] response = RequestResponseService.sendCommand(serial, aarq);
//            GXDLMS.getData(client.getSettings(), new GXReplyData(), response);
            session.setAssociated(true);
        }

        // Now read OBIS
        // ...
        return null;
    }


    @Scheduled(fixedRate = 60000) // Every minute
    public void cleanupIdleSessions() {
        sessionManager.cleanupExpiredSessions(Duration.ofMinutes(5));
    }
}
