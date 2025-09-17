package com.memmcol.hes.service;

import com.memmcol.hes.model.ModelProfileMetadata;
import com.memmcol.hes.repository.ModelProfileMetadataRepository;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXDateTime;
import gurux.dlms.GXReplyData;
import gurux.dlms.GXStructure;
import gurux.dlms.enums.DateTimeSkips;
import gurux.dlms.enums.ObjectType;
import gurux.dlms.objects.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static com.memmcol.hes.service.DlmsDateCodec.parseDlmsTimestampObject;

@Slf4j
@Service
public class DlmsUtils {
    /**
     * Create and return the appropriate DLMS object from a given classId and OBIS code.
     */
    public static GXDLMSObject createDlmsObject(int classId, String obis) {
        return switch (classId) {
            case 1  -> new GXDLMSData();
            case 3  -> new GXDLMSRegister();
            case 4  -> new GXDLMSDemandRegister();
            case 5  -> new GXDLMSExtendedRegister();
            case 7  -> new GXDLMSProfileGeneric();
            case 8  -> new GXDLMSClock(); // 🔁 Added support for Clock
            case 70 -> new GXDLMSDisconnectControl(); // Disconnect unit
            case 71 -> new GXDLMSLimiter();           // Load limiter
            // Add more if needed (e.g., 11 → Register Activation, 15 → Script Table)
            default -> throw new IllegalArgumentException("Unsupported DLMS classId: " + classId);
        };
    }

    /**
     * Populate the capture objects in a GXDLMSProfileGeneric from metadata list.
     *
     * @param profile       The profile object to configure.
     * @param metadataList  List of metadata for captured columns.
     */
    public static void populateCaptureObjects(GXDLMSProfileGeneric profile, List<ModelProfileMetadata> metadataList) {
        for (ModelProfileMetadata meta : metadataList) {
            GXDLMSObject obj = createDlmsObject(meta.getClassId(), meta.getCaptureObis());
            profile.addCaptureObject(obj, meta.getAttributeIndex(), 0);  // dataIndex is often 0
        }
    }

//    🛠 Utility Helper

    public static GXDateTime toGXDateTime(LocalDateTime dt) {
        GXDateTime gxdt = new GXDateTime();
        gxdt.setValue(java.sql.Timestamp.valueOf(dt));
        // ⏱️ Use full timestamp, no skips
        gxdt.setSkip(EnumSet.noneOf(DateTimeSkips.class));
        return gxdt;
    }

    @SuppressWarnings("unchecked")
    public LocalDateTime extractFirstTimestampFromProfileBuffer(GXDLMSProfileGeneric profile) {
        Object bufferObj = profile.getBuffer();
        if (bufferObj == null) {
            log.debug("Profile buffer is null.");
            return null;
        }

        // Gurux returns either List<?> or Object[] depending on decode
        List<Object> rows;
        if (bufferObj instanceof List<?> l) {
            rows = (List<Object>) l;
        } else if (bufferObj instanceof Object[] arr) {
            rows = Arrays.asList(arr);
        } else {
            log.debug("Unexpected buffer type: {}", bufferObj.getClass());
            return null;
        }

        if (rows.isEmpty()) return null;

        Object row0 = rows.get(0);

        // Normal case: GXStructure
        if (row0 instanceof GXStructure s) {
            return parseTimestampFromStructure(s, profile.getCaptureObjects());
        }

        // Some meters => flat Object[]
        if (row0 instanceof Object[] arrRow) {
            return parseTimestampFromArray(arrRow);
        }

        // Rare case: timestamp direct
        if (row0 instanceof GXDateTime gxdt) {
            return gxdt.getValue()
                    .toInstant()
                    .atZone(TimeZone.getDefault().toZoneId())
                    .toLocalDateTime();
        }
        if (row0 instanceof byte[] b) {
            return DlmsDateCodec.decode(b, TimeZone.getDefault().toZoneId()).dateTime();
        }

        log.debug("Unhandled first row type: {}", row0.getClass());
        return null;
    }

    //    3. Helpers: Structure & Array Timestamp Extraction

    public LocalDateTime parseTimestampFromStructure(
            GXStructure s,
            List<Map.Entry<GXDLMSObject, GXDLMSCaptureObject>> captureObjects) {

        // Find CLOCK column index
        int clockIdx = -1;
        for (int i = 0; i < captureObjects.size(); i++) {
            GXDLMSObject obj = captureObjects.get(i).getKey();
            if (obj instanceof GXDLMSClock) {
                clockIdx = i;
                break;
            }
        }
        if (clockIdx < 0 || clockIdx >= s.size()) {
            log.debug("No clock column found in captureObjects.");
            return null;
        }

        Object tsRaw = s.get(clockIdx);
        DlmsDateCodec.ParsedTs p = parseDlmsTimestampObject(tsRaw, TimeZone.getDefault().toZoneId());
        return p.dateTime();
    }

    public LocalDateTime parseTimestampFromArray(Object[] arr) {
        if (arr.length == 0) return null;
        DlmsDateCodec.ParsedTs p = parseDlmsTimestampObject(arr[0], TimeZone.getDefault().toZoneId());
        return p.dateTime();
    }



}
