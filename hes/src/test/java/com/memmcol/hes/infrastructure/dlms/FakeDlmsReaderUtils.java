package com.memmcol.hes.infrastructure.dlms;

import com.memmcol.hes.domain.profile.ProfileMetadataResult;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXReplyData;
import gurux.dlms.GXStructure;
import gurux.dlms.enums.Authentication;
import gurux.dlms.enums.InterfaceType;
import gurux.dlms.internal.GXCommon;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FakeDlmsReaderUtils {
//    public FakeDlmsReaderUtils(SessionManager sessionManager, MeterLockPort meterLockPort, DlmsPartialDecoder partialDecoder, DlmsTimestampDecoder timestampDecoder) {
//        super(sessionManager, meterLockPort, partialDecoder, timestampDecoder);
//    }

    private List<List<Object>> cannedRows = new ArrayList<>();

    public FakeDlmsReaderUtils() {
                // === Example: Add multiple frames ===
        decodeAndAddRow(
                "00 01 00 01 00 01 00 91 C4 01 C1 00 01 01 02 16 09 0C 07 E8 0A 01 02 00 00 00 00 FF C4 00 06 00 6D EE 1D" // Frame #1
        );
        decodeAndAddRow(
                "00 01 00 01 00 01 00 91 C4 01 C1 00 01 01 02 16 09 0C 07 E9 0B 01 02 00 00 00 00 FF C4 00 06 00 75 AA 3B" // Frame #2
        );
        // ⬆️ Replace with 2nd frame from your log (just an example)
    }

    private void decodeAndAddRow(String rxHex) {
        try {
            byte[] frame = GXCommon.hexToBytes(rxHex.replace(" ", ""));
            GXDLMSClient client = new GXDLMSClient(
                    true, 1, 1,
                    Authentication.LOW,
                    "12345678",
                    InterfaceType.WRAPPER);
            GXReplyData reply = new GXReplyData();
            client.getData(frame, reply);

            Object value = reply.getValue();
            List<List<Object>> rows = new ArrayList<>();

            if (value instanceof GXStructure struct) {
                // Case 1️⃣: single row
                rows.add(decodeStructure(struct));
            } else if (value instanceof List<?> array) {
                // Case 2️⃣: multiple rows (GXArray)
                for (Object elem : array) {
                    if (elem instanceof GXStructure struct) {
                        rows.add(decodeStructure(struct));
                    }
                }
            }
                // Save canned rows
            cannedRows = rows;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static List<Object> decodeStructure(GXStructure struct) {
        List<Object> row = new ArrayList<>();
        for (Object element : struct) {
            if (element instanceof byte[] rawTs) {
                row.add(toLocalDateTime(rawTs));
            } else {
                row.add(element);
            }
        }
        return row;
    }

    private static LocalDateTime toLocalDateTime(byte[] raw) {
        try {
            return LocalDateTime.ofInstant(
                    GXCommon.getDateTime(raw).getValue().toInstant(),
                    ZoneId.systemDefault()
            );
        } catch (Exception e) {
            return null;
        }
    }

    public List<List<Object>> readRange(String model,
                                             String serial,
                                             String profileObis,
                                             ProfileMetadataResult metadata,
                                             LocalDateTime from,
                                             LocalDateTime to,
                                             boolean mdMeter) {
        if ("0.0.98.1.0.255".equals(profileObis)) {
            return cannedRows; // return all decoded rows
        }
        return Collections.emptyList();
    }


}
