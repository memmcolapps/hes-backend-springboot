package com.memmcol.hes.security;

import com.memmcol.hes.security.util.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;


@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {
    private final JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) throws Exception {

        log.debug("Handshake attempt: {}", request.getURI());
        URI uri = request.getURI();
        String query = uri.getQuery();
        log.debug("URI: {}", query);
        String token = null;
        if (query != null && query.contains("token=")) {
            token = Arrays.stream(query.split("&"))
                    .filter(p -> p.startsWith("token="))
                    .map(p -> p.substring("token=".length()))
                    .findFirst()
                    .orElse(null);
        }

        if (token == null || token.isBlank()) {
            log.warn("⛔ Missing token in WebSocket handshake");
            return false;
        }

        try {
            jwtUtil.validateToken(token); // throws if invalid
            attributes.put("clientId", jwtUtil.extractClientId(token));
            log.info("✅ WebSocket JWT valid, client: {}", attributes.get("clientId"));
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("⏰ JWT expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("🚫 Unsupported JWT: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("❗ Malformed JWT: {}", e.getMessage());
        } catch (SecurityException e) {
            log.warn("🔐 Invalid JWT signature: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ JWT claims string is empty: {}", e.getMessage());
        } catch (JwtException e) {
            log.warn("❌ JWT error: {}", e.getMessage());
        }
        // Default fallback for any JWT error
        log.error("🔒 WebSocket handshake rejected due to JWT validation failure");
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest req, ServerHttpResponse res,
                               WebSocketHandler handler, Exception ex) {
    }
}



