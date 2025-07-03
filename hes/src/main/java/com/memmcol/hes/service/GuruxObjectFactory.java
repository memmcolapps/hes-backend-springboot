package com.memmcol.hes.service;

import gurux.dlms.GXDLMSClient;
import gurux.dlms.enums.ObjectType;
import gurux.dlms.objects.GXDLMSObject;
import org.springframework.stereotype.Service;

@Service
public class GuruxObjectFactory {
    public static GXDLMSObject create(int classId, String logicalName) {
        ObjectType type = ObjectType.forValue(classId);

        GXDLMSObject obj = GXDLMSClient.createObject(type);
        obj.setLogicalName(logicalName);

        return obj;
    }
}
