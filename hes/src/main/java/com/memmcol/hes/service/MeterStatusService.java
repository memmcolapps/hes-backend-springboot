package com.memmcol.hes.service;

import com.memmcol.hes.model.MeterStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MeterStatusService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public void broadcastMeterStatus(String meterSerial, String status) {
        MeterStatus meterStatus  = new MeterStatus(meterSerial, status, System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/meter-status", meterStatus);
        log.debug("Broadcasting status: {} for meter: {}", status, meterSerial);
    }

    public void broadcastMeterOnline(String serial) {
        broadcastMeterStatus(serial, "ONLINE");
    }

    public void broadcastMeterOffline(String serial) {
        broadcastMeterStatus(serial, "OFFLINE");
    }
}
