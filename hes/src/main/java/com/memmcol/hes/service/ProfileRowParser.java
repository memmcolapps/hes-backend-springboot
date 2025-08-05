package com.memmcol.hes.service;

import com.memmcol.hes.model.ProfileMetadataDTO;
import com.memmcol.hes.model.ProfileRowDTO;
import gurux.dlms.GXByteBuffer;
import gurux.dlms.GXDateTime;
import gurux.dlms.GXStructure;
import gurux.dlms.enums.ObjectType;
import gurux.dlms.internal.GXCommon;
import gurux.dlms.internal.GXDataInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static com.memmcol.hes.service.DlmsService.GLOBAL_TS_FORMATTER;

@Service
@Slf4j
public class ProfileRowParser {

    /**
     * Parse DLMS profile data from a list of rows and column metadata.
     *
     * @param columns    the metadata describing each column
     * @param replyValue the reply.getValue() (should be List<GXStructure>)
     * @param startEntry the first entryId number to assign
     * @return parsed profile rows
     */

    public List<ProfileRowDTO> parse(List<ProfileMetadataDTO.ColumnDTO> columns, Object replyValue, int startEntry) {
        List<ProfileRowDTO> result = new ArrayList<>();

        // 🧠 Decode raw GXByteBuffer if needed
        if (replyValue instanceof GXByteBuffer buffer) {
            GXDataInfo info = new GXDataInfo();
            buffer.position(0); // 👈 Ensure reading starts from beginning
            replyValue = GXCommon.getData(null, buffer, info);
        }

        if (!(replyValue instanceof List<?> rows)) {
            log.warn("⚠️ Unexpected reply format: {}", replyValue == null ? "null" : replyValue.getClass());
            return result;
        }

        int entryId = startEntry;

        for (Object rowObj : rows) {
            if (!(rowObj instanceof GXStructure structure)) {
                log.warn("⚠️ Skipping non-GXStructure row: {}", rowObj.getClass());
                continue;
            }

            Map<String, Object> mappedValues = new LinkedHashMap<>();
            List<Object> rawValues = new ArrayList<>();
            LocalDateTime timestamp = null;

            for (int i = 0; i < columns.size() && i < structure.size(); i++) {
                ProfileMetadataDTO.ColumnDTO col = columns.get(i);
                Object val = structure.get(i);

                if (col.getClassId() == ObjectType.CLOCK.getValue()) {
                    // ✅ Parse DLMS clock object
                    LocalDateTime parsed = DlmsDateUtils.parseTimestampLdt(val);
                    if (parsed != null) {
                        mappedValues.put("timestamp", parsed.format(GLOBAL_TS_FORMATTER));
                        rawValues.add(parsed.format(GLOBAL_TS_FORMATTER));
                        timestamp = parsed; // 🕒 Save LocalDateTime for deduplication
                    } else {
                        rawValues.add(val);
                    }
                } else {
                    mappedValues.put(col.getObis(), val);
                    rawValues.add(val);
                }
            }

            ProfileRowDTO row = new ProfileRowDTO();
            row.setEntryId(entryId++);
            row.setValues(mappedValues);
            row.setRawData(rawValues);
            row.setProfileTimestamp(timestamp); // Optional: useful downstream

//            // ✅ Deduplication (optional but recommended)
//            LocalDateTime finalTimestamp = timestamp;
//            if (timestamp != null && result.stream().anyMatch(r ->
//                    finalTimestamp.equals(r.getProfileTimestamp()))) {
//                log.warn("⚠️ Duplicate timestamp {} found — skipping row.", timestamp);
//                continue;
//            }

            result.add(row);
        }

        return result;
    }
//    public List<ProfileRowDTO> parse(List<ProfileMetadataDTO.ColumnDTO> columns, Object replyValue, int startEntry) {
//        List<ProfileRowDTO> result = new ArrayList<>();
//
//        // 💡 If raw bytes, attempt to decode into List<Structure>
//        if (replyValue instanceof GXByteBuffer buffer) {
//            GXDataInfo info = new GXDataInfo();
//            buffer.position(0);
//            Object decoded = GXCommon.getData(null,buffer, info);
//            replyValue = decoded;
//        }
//
//        if (!(replyValue instanceof List<?> rows)) {
//            log.warn("⚠️ Unexpected reply value format: {}", replyValue == null ? "null" : replyValue.getClass());
//            return result;
//        }
//
//        int entryId = startEntry;
//
//        for (Object rowObj : rows) {
//            GXStructure structure;
//            try {
//                structure = (GXStructure) rowObj;
//            } catch (ClassCastException e) {
//                log.warn("⚠️ Row is not a GXStructure: {}", rowObj.getClass());
//                continue;
//            }
//            ProfileRowDTO row = new ProfileRowDTO();
//            row.setEntryId(entryId++); // ✅ Track entry number
//
//            for (int i = 0; i < columns.size() && i < structure.size(); i++) {
//                ProfileMetadataDTO.ColumnDTO col = columns.get(i);
//                Object val = structure.get(i);
//
//                if (col.getClassId() == ObjectType.CLOCK.getValue()) {
//                    DlmsDateUtils.ParsedTimestamp parsed = DlmsDateUtils.parseTimestamp(val, i);
//                    if (parsed != null) {
//                        row.getValues().put("timestamp", parsed.formatted); // ✅ Add this
//                    }
//                } else {
//                    row.getValues().put(col.getObis(), val);
//
//                }
//            }
//
//            result.add(row);
//        }
//
//        return result;
//    }

}

