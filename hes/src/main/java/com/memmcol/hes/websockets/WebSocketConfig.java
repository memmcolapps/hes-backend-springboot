package com.memmcol.hes.websockets;

import com.memmcol.hes.security.WebSocketHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final WebSocketHandshakeInterceptor handshakeInterceptor;
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Native WebSocket support
        registry.addEndpoint("/ws-meters")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*")
                .withSockJS(); // no SockJS

        // SockJS fallback
        registry.addEndpoint("/ws-meters")
                .setAllowedOriginPatterns("*")
                .addInterceptors(handshakeInterceptor);
    }
    @Override
    public void configureMessageBroker(MessageBrokerRegistry cfg) {
        cfg.enableSimpleBroker("/topic");
        cfg.setApplicationDestinationPrefixes("/app");
    }
}

