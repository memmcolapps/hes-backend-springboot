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

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

        // 💡 If raw bytes, attempt to decode into List<Structure>
        if (replyValue instanceof GXByteBuffer buffer) {
            GXDataInfo info = new GXDataInfo();
            buffer.position(0);
            Object decoded = GXCommon.getData(null,buffer, info);
            replyValue = decoded;
        }

        if (!(replyValue instanceof List<?> rows)) {
            log.warn("⚠️ Unexpected reply value format: {}", replyValue == null ? "null" : replyValue.getClass());
            return result;
        }

        int entryId = startEntry;

        for (Object rowObj : rows) {
            GXStructure structure;
            try {
                structure = (GXStructure) rowObj;
            } catch (ClassCastException e) {
                log.warn("⚠️ Row is not a GXStructure: {}", rowObj.getClass());
                continue;
            }
            ProfileRowDTO row = new ProfileRowDTO();
            row.setEntryId(entryId++); // ✅ Track entry number

            for (int i = 0; i < columns.size() && i < structure.size(); i++) {
                ProfileMetadataDTO.ColumnDTO col = columns.get(i);
                Object val = structure.get(i);

                if (col.getClassId() == ObjectType.CLOCK.getValue()) {
                    DlmsDateUtils.ParsedTimestamp parsed = DlmsDateUtils.parseTimestamp(val, i);
                    if (parsed != null) {
                        row.getValues().put("timestamp", parsed.formatted); // ✅ Add this
                    }
                } else {
                    row.getValues().put(col.getObis(), val);
                }
            }

            result.add(row);
        }

        return result;
    }

}

