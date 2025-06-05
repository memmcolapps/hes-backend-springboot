package com.memmcol.hes.websockets;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    // 1. Configure the message broker (for topic-based broadcasting)
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic"); // frontend subscribes to /topic/meter-status
        config.setApplicationDestinationPrefixes("/app"); // For sending messages to server (optional)
    }

    // 2. Register the WebSocket endpoint (frontend connects to this)
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-meters")  // WebSocket connection URL (e.g., ws://localhost:8080/ws)
                .setAllowedOriginPatterns("*")
                .withSockJS();                      // Fallback for browsers that don’t support WebSocket
    }
}
