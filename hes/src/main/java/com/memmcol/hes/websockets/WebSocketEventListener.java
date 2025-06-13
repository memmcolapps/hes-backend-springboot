package com.memmcol.hes.websockets;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.*;

@Slf4j
@Component
public class WebSocketEventListener {

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.info("🟢 STOMP Connect: sessionId={}, headers={}",
                accessor.getSessionId(), accessor.toNativeHeaderMap());
    }

    @EventListener
    public void handleWebSocketSubscribeListener(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.info("📡 STOMP Subscribe: sessionId={}, destination={}, headers={}",
                accessor.getSessionId(), accessor.getDestination(), accessor.toNativeHeaderMap());
    }

    @EventListener
    public void handleWebSocketUnsubscribeListener(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.info("📴 STOMP Unsubscribe: sessionId={}, headers={}",
                accessor.getSessionId(), accessor.toNativeHeaderMap());
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.info("🔴 STOMP Disconnect: sessionId={}, Close status={}",
                accessor.getSessionId(), event.getCloseStatus());
    }
}
