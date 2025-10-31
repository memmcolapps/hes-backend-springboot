package com.memmcol.hes.model;

import gurux.dlms.enums.AccessMode;
import gurux.dlms.objects.GXDLMSObject;
import gurux.dlms.objects.GXDLMSRegister;

import java.time.LocalDateTime;

public class DlmsObisMapper {
//    public static ObisObjectDTO map(GXDLMSObject obj) {
//        ObisObjectDTO dto = new ObisObjectDTO();
//        dto.setObisCode(obj.getLogicalName());
//        dto.setClassId(obj.getObjectType().getValue());
//        dto.setVersion(obj.getVersion());
//        dto.setType(obj.getObjectType().toString());
//        dto.setAttributeCount(obj.getAttributeCount());
//
//        StringBuilder rights = new StringBuilder();
//        for (int i = 1; i <= obj.getAttributeCount(); i++) {
////            AccessMode mode = obj.getAccess().get(i);
//            AccessMode mode = obj.getAccess(i);
//            String r = (mode == null) ? "N" : switch (mode) {
//                case READ -> "R";
//                case WRITE -> "W";
//                case READ_WRITE -> "R/W";
//                default -> "N";
//            };
//            rights.append(i).append(":").append(r);
//            if (i < obj.getAttributeCount()) rights.append(", ");
//        }
//        dto.setAccessRights(rights.toString());
//
//        if (obj instanceof GXDLMSRegister reg) {
//            dto.setScaler(String.valueOf(reg.getScaler()));
//            dto.setUnit(reg.getUnit().toString());
//        }
//        return dto;
//    }

    public static ObisObjectDTO map(GXDLMSObject obj, String meterSerial, String meterModel) {
        ObisObjectDTO dto = new ObisObjectDTO();
        dto.setMeterSerial(meterSerial);
        dto.setMeterModel(meterModel);
        dto.setObisCode(obj.getLogicalName());
        dto.setClassId(obj.getObjectType().getValue());
        dto.setVersion(obj.getVersion());
        dto.setType(obj.getObjectType().toString());
        dto.setAttributeCount(obj.getAttributeCount());

        StringBuilder rights = new StringBuilder();
        for (int i = 1; i <= obj.getAttributeCount(); i++) {
//            AccessMode mode = obj.getAccess().get(i);
            AccessMode mode = obj.getAccess(i);
            String r = (mode == null) ? "N" : switch (mode) {
                case READ -> "R";
                case WRITE -> "W";
                case READ_WRITE -> "R/W";
                default -> "N";
            };
            rights.append(i).append(":").append(r);
            if (i < obj.getAttributeCount()) rights.append(", ");
        }
        dto.setAccessRights(rights.toString());

        if (obj instanceof GXDLMSRegister reg) {
            dto.setScaler(String.valueOf(reg.getScaler()));
            dto.setUnit(reg.getUnit().toString());
        }
        return dto;
    }

    public static DlmsObisObjectEntity toEntity(ObisObjectDTO dto) {
        return new DlmsObisObjectEntity(
                null,
                dto.getObisCode(),
                dto.getClassId(),
                dto.getVersion(),
                dto.getType(),
                dto.getAttributeCount(),
                dto.getAccessRights(),
                dto.getScaler(),
                dto.getUnit(),
                dto.getMeterSerial(),
                dto.getMeterModel(),
                LocalDateTime.now()
        );
    }
}
