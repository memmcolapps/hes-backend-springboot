package com.memmcol.hes.nettyUtils;

import com.memmcol.hes.infrastructure.dlms.DlmsDataDecoder;
import gurux.dlms.*;
import gurux.dlms.enums.Authentication;
import gurux.dlms.enums.Command;
import gurux.dlms.enums.DataType;
import gurux.dlms.enums.InterfaceType;
import gurux.dlms.internal.GXCommon;
import gurux.dlms.objects.GXDLMSObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

@Slf4j
@Service
public class EventNotificationHandler {

    public void process(String serial, byte[] data) throws Exception {
        EventNotificationRecord record = new EventNotificationRecord(
                -1, "Error!", "",
                0, "", 0, LocalDateTime.now(), 0);
        GXDLMSClient client = new GXDLMSClient(
                true,                  // Use logical name referencing
                1,                     // Client address
                1,                     // Server address (for WRAPPER, not strict)
                Authentication.NONE,   // Authentication level
                null,                  // Password (none)
                InterfaceType.WRAPPER  // WRAPPER interface
        );

        GXReplyData reply = new GXReplyData();
        client.getData(data, reply);

        if (!reply.isComplete()) {
            log.error("Incomplete DLMS event data!");
            return;
        }

        if (reply.getCommand() == Command.EVENT_NOTIFICATION) {
            record = getValue(reply, serial);
        }

//        // --- 9️⃣ Save to database
//        eventNotificationService.save(record);
//
//        // --- 🔟 Optional alert for critical events
//        if (isCritical(record.eventCode())) {
//            smsService.sendAlert(serial, "Critical event detected: " + record.eventCode() + " at " + record.timestamp());
//        }
    }

    public EventNotificationRecord getValue(GXReplyData reply, String serial) throws Exception {
        GXByteBuffer bb = new GXByteBuffer(reply.getData());

        // 1️⃣ Skip invoke ID / priority if it’s 0x00
        if (bb.size() > 0 && bb.getUInt8(0) == 0x00) {
            bb.getUInt8(); // discard it
        }

        // --- 1️⃣ Class ID (2 bytes)
        int classId = bb.getUInt16();

        // --- 2️⃣ OBIS code (6 bytes)
        byte[] obis = new byte[6];
        bb.get(obis);
        String obisCode = GXCommon.toLogicalName(obis);

        // --- 3️⃣ Attribute ID
        int attributeId = bb.getUInt8();

        // --- 4️⃣ DataType (expect ARRAY)
        int dataType = bb.getUInt8();
        if (dataType != DataType.ARRAY.getValue()) {
            return new EventNotificationRecord(
                    -1, "Not an ARRAY type!", "",
                    0, "", 0, LocalDateTime.now(), 0);
        }

        int arrayCount = bb.getUInt8(); // usually 1

        // --- 5️⃣ Inner structure
        int innerType = bb.getUInt8();
        if (innerType != DataType.STRUCTURE.getValue()) {
            return new EventNotificationRecord(
                    -1, "Not a STRUCTURE!", "",
                    0, "", 0, LocalDateTime.now(), 0);
        }

        int structCount = bb.getUInt8(); // usually 2 (timestamp + event code)

        // --- 6️⃣ Timestamp (OCTET STRING)
        LocalDateTime decodedValue = LocalDateTime.now();
        int tsType = bb.getUInt8();
        if (tsType == DataType.OCTET_STRING.getValue()) {
            int len = bb.getUInt8();
            byte[] ts = new byte[len];
            bb.get(ts);
            decodedValue = (LocalDateTime) DlmsDataDecoder.decodeOctetString((byte[]) ts);
        } else {
            return new EventNotificationRecord(
                    -1, "Unexpected timestamp type!", "",
                    0, "", 0, LocalDateTime.now(), 0);
        }

        // --- 7️⃣ Event Code (UINT32)
        int codeType = bb.getUInt8();
        long eventCode = 0;
        if (codeType == DataType.UINT32.getValue()) {
            eventCode = bb.getUInt32();
        } else {
            return new EventNotificationRecord(
                    -1, "Unexpected event code type!", "",
                    0, "", 0, LocalDateTime.now(), 0);
        }

        // --- 8️⃣ Create Event object
        EventNotificationRecord record = new EventNotificationRecord(
                0, "Success",
                serial, classId, obisCode, attributeId,
                decodedValue, eventCode
        );
        log.info(record.toString());
        return record;
    }

    public byte[] hexToBytes(String hex) {
        hex = hex.replaceAll("\\s+", "");
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private boolean isCritical(long eventCode) {
        // You can refine this mapping later
        return switch ((int) eventCode) {
            case 29, 30, 31, 100 -> true; // example: power failure, cover open, etc.
            default -> false;
        };
    }

    public static void main(String[] args) throws Exception {
//        String data = "00 01 00 01 00 66 00 22 C2 00 00 07 00 00 63 62 01 FF 02 01 01 02 02 09 0C 07 E9 0A 1F 05 11 0F 39 FF 80 00 00 06 00 00 00 29";
//        EventNotificationHandler notificationHandler = new EventNotificationHandler();
//        byte[] frame = notificationHandler.hexToBytes(data);
//        notificationHandler.process("123456", frame);

        log.info("OS DEFAULT TIMEZONE: {}", ZoneId.systemDefault());
        log.info("JVM Default time zone: {}", TimeZone.getDefault().getID());
        log.info("JVM DEFAULT TIMEZONE: {}", TimeZone.getDefault());
        log.info("Current System time: {}", LocalDateTime.now());



    }


}
