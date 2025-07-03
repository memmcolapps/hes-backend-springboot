package com.memmcol.hes.model;

import gurux.dlms.GXDLMSClient;
import gurux.dlms.enums.ObjectType;
import gurux.dlms.objects.GXDLMSCaptureObject;
import gurux.dlms.objects.GXDLMSObject;
import gurux.dlms.objects.GXDLMSProfileGeneric;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class ProfileMetadataDTO implements Serializable {
    private int entriesInUse;
    private List<ColumnDTO> columns = new ArrayList<>();

    @Data
    @Getter
    @Setter
    public static class ColumnDTO {
        private String obis;
        private int attributeIndex;
        private int classId;
    }

    public static ProfileMetadataDTO from(GXDLMSProfileGeneric profile) {
        ProfileMetadataDTO dto = new ProfileMetadataDTO();
        dto.setEntriesInUse(profile.getEntriesInUse());

        for (Map.Entry<GXDLMSObject, GXDLMSCaptureObject> entry : profile.getCaptureObjects()) {
            ColumnDTO col = new ColumnDTO();
            GXDLMSObject obj = entry.getKey();

            col.setObis(obj.getLogicalName());
            col.setClassId(obj.getObjectType().getValue());

            // ❌ Skip attributeIndex if not accessible
            col.setAttributeIndex(2); // Default to 2, most common for Register/Clock
            dto.getColumns().add(col);
        }

        return dto;
    }


}
