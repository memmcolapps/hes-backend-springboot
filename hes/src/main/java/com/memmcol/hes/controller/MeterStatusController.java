package com.memmcol.hes.controller;

import com.memmcol.hes.models.MeterStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
@Slf4j
public class MeterStatusController {
    @MessageMapping("/send-status")
    @SendTo("/topic/meter-status")
    public MeterStatus receive(MeterStatus status, @Header("simpSessionAttributes") Map<String, Object> attrs) {
        String cid = (String) attrs.get("clientId");
        log.info("Received from {}: {}", cid, status);
        return status;
    }
}
