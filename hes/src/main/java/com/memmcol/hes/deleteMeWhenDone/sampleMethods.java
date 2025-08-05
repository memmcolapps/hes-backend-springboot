package com.memmcol.hes.deleteMeWhenDone;

import com.memmcol.hes.model.ProfileMetadataDTO;
import com.memmcol.hes.model.ProfileRowDTO;
import com.memmcol.hes.service.DlmsDateCodec;
import com.memmcol.hes.service.DlmsDateUtils;
import com.memmcol.hes.service.ProfileRowParserRaw;
import gurux.dlms.GXByteBuffer;
import gurux.dlms.GXDateTime;
import gurux.dlms.GXStructure;
import gurux.dlms.enums.ObjectType;
import gurux.dlms.internal.GXCommon;
import gurux.dlms.internal.GXDataInfo;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.memmcol.hes.service.DlmsDateCodec.parseDlmsTimestampObject;
import static com.memmcol.hes.service.DlmsService.GLOBAL_TS_FORMATTER;

@Slf4j


/*
This is the methods are using during development stages to decode raw values during profile readings
 */
public class sampleMethods {

    ProfileRowParserRaw rowParserRaw = new ProfileRowParserRaw();

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
                out.add(rowParserRaw.buildRowFromStructure(columns, (GXStructure) o, entryId++));
            }
            return out;
        }

        // Case B: pattern = ts, v1, v2, ts, v1, v2...
        if (isFlatTripletPattern(decodedObjs, columns.size())) {
            out.addAll(buildRowsFromFlatObjs(columns, decodedObjs, entryId));
            return out;
        }

        // Case C: some values are byte[] and represent embedded structures
        List<Object> flattened = new ArrayList<>();
        for (Object o : decodedObjs) {
            if (o instanceof byte[] enc && (enc[0] & 0xFF) == 0x02) {
                // decode nested structure
                GXByteBuffer b = new GXByteBuffer(enc);
                GXDataInfo info = new GXDataInfo();
                Object nested = GXCommon.getData(null, b, info);
                flattened.add(nested);
            } else {
                flattened.add(o);
            }
        }

        // recurse once after flattening
        if (!flattened.equals(decodedObjs)) {
            return parseDecodedObjects(columns, flattened, startEntry);
        }

        log.warn("parseDecodedObjects: unrecognized mix {}", decodedObjs);
        return out;
    }

    //    ✅ Detect Flat Pattern

    private boolean isFlatTripletPattern(List<Object> objs, int stride) {
        if (objs.size() < stride) return false;
        if (objs.size() % stride != 0) return false;
        // First item must be ts (GXDateTime or byte[])
        Object first = objs.get(0);
        return (first instanceof GXDateTime) || (first instanceof byte[]);
    }

    //    ✅ Build Rows From Decoded Object List

    private List<ProfileRowDTO> buildRowsFromFlatObjs(
            List<ProfileMetadataDTO.ColumnDTO> cols,
            List<Object> objs,
            int startEntry
    ) {
        List<ProfileRowDTO> result = new ArrayList<>();
        int stride = cols.size();
        int entryId = startEntry;
        ZoneId zone = ZoneId.of("Africa/Lagos");

        for (int i = 0; i + stride <= objs.size(); i += stride) {
            Map<String,Object> map = new LinkedHashMap<>();
            List<Object> raw = new ArrayList<>();
            LocalDateTime ts = null;

            // timestamp
            Object tsRaw = objs.get(i);
            DlmsDateCodec.ParsedTs pts = parseDlmsTimestampObject(tsRaw, zone);
            if (pts.dateTime() != null) {
                map.put("timestamp", pts.formatted());
                raw.add(pts.formatted());
                ts = pts.dateTime();
            } else if (tsRaw instanceof byte[] b) {
                String hex = GXCommon.toHex(b);
                map.put("timestamp", hex);
                raw.add(hex);
            } else {
                map.put("timestamp", tsRaw);
                raw.add(tsRaw);
            }

            // channels
            for (int c = 1; c < stride; c++) {
                Object v = objs.get(i + c);
                ProfileMetadataDTO.ColumnDTO col = cols.get(c);
                map.put(col.getObis(), v);
                raw.add(v);
            }

            ProfileRowDTO row = new ProfileRowDTO();
            row.setEntryId(entryId++);
            row.setProfileTimestamp(ts);
            row.setValues(map);
            row.setRawData(raw);
            result.add(row);
        }
        return result;
    }

    //✅ Build Rows From Flat Triplets
//
//    Use capture-object count as stride (typically 3: timestamp + active + reactive). If more capture objects, stride matches metadata size.
    private List<ProfileRowDTO> buildRowsFromFlatTriplets(
            List<ProfileMetadataDTO.ColumnDTO> columns,
            List<Object> flat,
            int startEntry
    ) {
        List<ProfileRowDTO> result = new ArrayList<>();
        int stride = columns.size(); // Expected columns per row
        if (stride <= 0) return result;

        for (int i = 0; i + stride <= flat.size(); i += stride) {
            ProfileRowDTO row = new ProfileRowDTO();
            row.setEntryId(startEntry++);

            Map<String, Object> mapped = new LinkedHashMap<>();
            List<Object> raw = new ArrayList<>();

            for (int j = 0; j < stride; j++) {
                Object val = flat.get(i + j);
                ProfileMetadataDTO.ColumnDTO col = columns.get(j);

                if (col.getClassId() == ObjectType.CLOCK.getValue()) {
                    // Decode timestamp if GXDateTime or byte[]
                    LocalDateTime ts = DlmsDateUtils.parseTimestampLdt(val);
                    mapped.put("timestamp", ts != null ? ts.format(GLOBAL_TS_FORMATTER) : val);
                    raw.add(ts != null ? ts.format(GLOBAL_TS_FORMATTER) : val);
                } else {
                    mapped.put(col.getObis(), val);
                    raw.add(val);
                }
            }

            row.setValues(mapped);
            row.setRawData(raw);
            result.add(row);
        }

        return result;
    }

    //    ✅ New: Decode One Encoded Structure Byte Array

    private ProfileRowDTO decodeEncodedStructureBytes(
            List<ProfileMetadataDTO.ColumnDTO> cols,
            byte[] encoded,
            int entryId
    ) {
        if (encoded == null || encoded.length == 0) return null;

        // quick sanity: expect STRUCTURE tag 0x02
        if ((encoded[0] & 0xFF) != 0x02) {
            log.warn("decodeEncodedStructureBytes: first byte 0x{} not STRUCTURE. Hex={}",
                    Integer.toHexString(encoded[0] & 0xFF), GXCommon.toHex(encoded));
        }

        GXByteBuffer buf = new GXByteBuffer(encoded);
        GXDataInfo info = new GXDataInfo();
        info.setComplete(false); // ensure decode start
        Object decoded = GXCommon.getData(null, buf, info);

        if (decoded instanceof GXStructure s) {
            return rowParserRaw.buildRowFromStructure(cols, s, entryId);
        }

        if (decoded instanceof List<?> list) {
            return buildRowFromArray(cols, list.toArray(), entryId);
        }

        log.warn("decodeEncodedStructureBytes: could not decode structure. Type={}, hex={}",
                decoded == null ? "null" : decoded.getClass(), GXCommon.toHex(encoded));
        return null;
    }

    //    B. Build Rows from Flat List (Grouped Triples)

    public ProfileRowDTO buildRowFromArray(List<ProfileMetadataDTO.ColumnDTO> cols,
                                           Object[] arr,
                                           int entryId) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        List<Object> raw = new ArrayList<>();
        LocalDateTime ts = null;

        // Timestamp first
        if (arr.length > 0) {
            LocalDateTime parsed = DlmsDateUtils.parseTimestampLdt(arr[0]);
            if (parsed != null) {
                mapped.put("timestamp", parsed.format(GLOBAL_TS_FORMATTER));
                raw.add(parsed.format(GLOBAL_TS_FORMATTER));
                ts = parsed;
            } else {
                raw.add(arr[0]);
            }
        }

        // Channels
        for (int i = 1; i < arr.length && i < cols.size(); i++) {
            ProfileMetadataDTO.ColumnDTO col = cols.get(i);
            Object val = arr[i];
            mapped.put(col.getObis(), val);
            raw.add(val);
        }

        ProfileRowDTO row = new ProfileRowDTO();
        row.setEntryId(entryId);
        row.setValues(mapped);
        row.setRawData(raw);
        row.setProfileTimestamp(ts);
        return row;
    }

    private List<ProfileRowDTO> buildRowsFromFlatList(List<ProfileMetadataDTO.ColumnDTO> cols,
                                                      List<?> flat,
                                                      int startEntry) {
        List<ProfileRowDTO> out = new ArrayList<>();
        int entryId = startEntry;
        int colCount = Math.min(cols.size(), 3); // timestamp + 2 channels expected

        for (int i = 0; i < flat.size(); i += colCount) {
            Object[] arr = new Object[colCount];
            for (int j = 0; j < colCount && (i + j) < flat.size(); j++) {
                arr[j] = flat.get(i + j);
            }
            out.add(buildRowFromArray(cols, arr, entryId++));
        }
        return out;
    }


    public List<ProfileRowDTO> parse(List<ProfileMetadataDTO.ColumnDTO> columns, Object replyValue, int startEntry) {
        List<ProfileRowDTO> result = new ArrayList<>();

        // 🔍 Decode raw GXByteBuffer to structured List if needed
        if (replyValue instanceof GXByteBuffer buffer) {
            GXDataInfo info = new GXDataInfo();
            buffer.position(0);
            replyValue = GXCommon.getData(null, buffer, info);
        }

        if (!(replyValue instanceof List<?> rows)) {
            log.warn("⚠️ Unexpected reply format: {}", replyValue == null ? "null" : replyValue.getClass());
            return result;
        }

        int entryId = startEntry;

        for (Object rowObj : rows) {
            GXStructure structure;
            try {
                structure = (GXStructure) rowObj;
            } catch (ClassCastException e) {
                log.warn("⚠️ Not a GXStructure: {}", rowObj.getClass());
                continue;
            }

            ProfileRowDTO row = new ProfileRowDTO();
            row.setEntryId(entryId++); // ✅ Maintain entry number

            Map<String, Object> mappedValues = new LinkedHashMap<>();
            List<Object> rawValues = new ArrayList<>();

            for (int i = 0; i < columns.size() && i < structure.size(); i++) {
                ProfileMetadataDTO.ColumnDTO col = columns.get(i);
                Object val = structure.get(i);

                // ⏱ Parse timestamp field
                if (col.getClassId() == ObjectType.CLOCK.getValue()) {
                    LocalDateTime parsed = DlmsDateUtils.parseTimestampLdt(val);
                    if (parsed != null) {
                        mappedValues.put("timestamp", parsed.format(GLOBAL_TS_FORMATTER));
                        rawValues.add(parsed.format(GLOBAL_TS_FORMATTER)); // add to raw too
                    } else {
                        rawValues.add(val);
                    }
                } else {
                    mappedValues.put(col.getObis(), val);
                    rawValues.add(val);
                }
            }

            row.setValues(mappedValues); // 🗂️ OBIS → value mapping
            row.setRawData(rawValues);   // 🧾 Ordered raw values

            result.add(row);
        }

        return result;
    }

}
