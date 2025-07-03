package com.memmcol.hes.service;

import com.memmcol.hes.model.ProfileRowDTO;
import gurux.dlms.GXDateTime;
import gurux.dlms.GXStructure;
import gurux.dlms.internal.GXCommon;
import gurux.dlms.objects.GXDLMSCaptureObject;
import gurux.dlms.objects.GXDLMSClock;
import gurux.dlms.objects.GXDLMSObject;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ProfileCaptureObjectParser {
    public static List<ProfileRowDTO> parse(List<?> data,
                                            List<Map.Entry<GXDLMSObject, GXDLMSCaptureObject>> columns) {

        List<ProfileRowDTO> result = new ArrayList<>();
        int entryId = 0;

        for (Object item : data) {
            if (!(item instanceof GXStructure structure)) {
                continue; // skip unexpected rows
            }

            ProfileRowDTO row = new ProfileRowDTO();
            row.setEntryId(entryId++);

            for (int i = 0; i < structure.size() && i < columns.size(); i++) {
                Object val = structure.get(i);
                Map.Entry<GXDLMSObject, GXDLMSCaptureObject> entry = columns.get(i);

                GXDLMSObject obj = entry.getKey();
                String obis = obj.getLogicalName();

                if (obj instanceof GXDLMSClock && val != null) {
                    GXDateTime dateTime = (val instanceof GXDateTime dt)
                            ? dt
                            : GXCommon.getDateTime((byte[]) val);
                    DlmsDateUtils.ParsedTimestamp parsed = DlmsDateUtils.parseTimestamp(val, i);
                    row.getValues().put("timestamp", parsed.formatted); // ✅ Add this
                } else {
                    row.getValues().put(obis, val);
                }
            }

            result.add(row);
        }

        return result;
    }
}
