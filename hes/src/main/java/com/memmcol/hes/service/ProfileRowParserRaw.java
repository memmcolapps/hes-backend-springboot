package com.memmcol.hes.service;

import com.memmcol.hes.model.ProfileMetadataDTO;
import com.memmcol.hes.model.ProfileRowDTO;
import gurux.dlms.GXByteBuffer;
import gurux.dlms.GXDateTime;
import gurux.dlms.GXReplyData;
import gurux.dlms.GXStructure;
import gurux.dlms.enums.ObjectType;
import gurux.dlms.internal.GXCommon;
import gurux.dlms.internal.GXDataInfo;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static com.memmcol.hes.service.DlmsDateCodec.parseDlmsTimestampObject;

@Slf4j
public class ProfileRowParserRaw {

    public List<ProfileRowDTO> parseBlock(
            List<ProfileMetadataDTO.ColumnDTO> columns,
            Object replyValue,
            int startEntry
    ) {
        List<ProfileRowDTO> out = new ArrayList<>();
        int entryId = startEntry;

        // If raw buffer, decode once.
        if (replyValue instanceof GXByteBuffer buf) {
            GXDataInfo info = new GXDataInfo();
            buf.position(0);
            replyValue = GXCommon.getData(null, buf, info);
        }

        // Expect array/list from Gurux
        if (replyValue instanceof List<?> rows) {

            //✅ For List<byte[]> or raw byte arrays inside:
            for (int i = 0; i < rows.size(); i++) {
                Object item = rows.get(i);
                if (item instanceof byte[] arr) {
                    log.debug("Row {} -> {}", i, GXCommon.toHex(arr)); // Gurux helper to hex
                } else {
                    log.debug("Row {} -> {}", i, item);
                }
            }

            if (!rows.isEmpty() && rows.get(0) instanceof GXStructure) {
                // Normal structured rows
                for (Object o : rows) {
                    ProfileRowDTO row = buildRowFromStructure(columns, (GXStructure) o, entryId++);
                    out.add(row);
                }
                return out;
            }
            log.warn("⚠️ parseBlock: Unsupported reply value type: {}", replyValue.getClass().getName());
            return out;
        }
        return out;
    }

//    Helpers
//    A. Build Row from GXStructure
    public ProfileRowDTO buildRowFromStructure(List<ProfileMetadataDTO.ColumnDTO> cols,
                                                GXStructure s,
                                                int entryId) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        List<Object> raw = new ArrayList<>();
        LocalDateTime ts = null;

        for (int i = 0; i < cols.size() && i < s.size(); i++) {
            Object val = s.get(i);
            ProfileMetadataDTO.ColumnDTO col = cols.get(i);

            if (col.getClassId() == ObjectType.CLOCK.getValue()) {
                LocalDateTime parsed = DlmsDateUtils.parseTimestampLdt(val);
                if (parsed != null) {
                    mapped.put("timestamp", parsed);
                    raw.add(parsed);
                    ts = parsed;
                } else {
                    raw.add(val);
                }
            } else {
                mapped.put(col.getObis(), val);
                raw.add(val);
            }
        }

        ProfileRowDTO row = new ProfileRowDTO();
        row.setEntryId(entryId);
        row.setValues(mapped);
        row.setRawData(raw);
        row.setProfileTimestamp(ts);
        return row;
    }

//    🔧 Utility: Decode All DLMS Objects From Block
    public List<Object> decodeAllObjectsFromReply(GXReplyData reply) {
        List<Object> objects = new ArrayList<>();

        GXByteBuffer data = reply.getData();
        if (data == null || data.size() == 0) {
            // Fallback: reply.getValue() only
            if (reply.getValue() != null) {
                objects.add(reply.getValue());
            }
            return objects;
        }

        // Clone so we don't disturb original buffer (optional but safer)
        GXByteBuffer buf = new GXByteBuffer(data.array());
        buf.position(0);

        while (buf.position() < buf.size()) {
            GXDataInfo info = new GXDataInfo();
            Object decoded = GXCommon.getData(null, buf, info);
            if (decoded == null) {
                break; // stop on parse error
            }
            objects.add(decoded);
        }

        return objects;
    }


    //    ✅ parseDecodedObjects(…) – smarter dispatcher
    public List<ProfileRowDTO> parseDecodedObjects(
            List<ProfileMetadataDTO.ColumnDTO> columns,
            List<Object> decodedObjs,
            int startEntry
    ) {
        List<ProfileRowDTO> out = new ArrayList<>();
        int entryId = startEntry;

        if (decodedObjs.isEmpty()) return out;

        // Case A: every element is GXStructure (best case)
        if (decodedObjs.stream().allMatch(o -> o instanceof GXStructure)) {
            for (Object o : decodedObjs) {
                out.add(buildRowFromStructure(columns, (GXStructure) o, entryId++));
            }
            return out;
        }

        log.warn("parseDecodedObjects: unrecognized mix {}", decodedObjs);
        return out;
    }

}
