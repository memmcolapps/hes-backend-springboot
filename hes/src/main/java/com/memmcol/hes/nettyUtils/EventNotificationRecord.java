package com.memmcol.hes.nettyUtils;

import java.time.LocalDateTime;

public record EventNotificationRecord(
        int responseCode,
        String responseMessage,
        String meterSerial,
        int classId,
        String obisCode,
        int attributeId,
        LocalDateTime timestamp,
        long eventCode
) {
}
