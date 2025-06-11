package com.memmcol.hes.security;

import com.memmcol.hes.security.util.JwtUtil;
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

//        if (token != null && jwtUtil.validateToken(token)) {
//            String username = jwtUtil.getUsernameFromToken(token);
//            attributes.put("username", username);
//            return true;
//        }

        if (token != null) {
            jwtUtil.validateToken(token); // throws if invalid
            attributes.put("clientId", jwtUtil.extractClientId(token));
            log.info("✅ WebSocket JWT valid, client: {}", attributes.get("clientId"));
            return true;
        }



    // Optional: Log reason for rejection
        log.error("❌ WebSocket rejected: missing or invalid token");

        return false;
}

//    @Override
//    public boolean beforeHandshake1(ServerHttpRequest req, ServerHttpResponse res,
//                                   WebSocketHandler handler, Map<String, Object> attrs) throws Exception {
//        log.debug("Handshake attempt: {}", req.getURI());
//        if (req instanceof ServletServerHttpRequest sreq) {
//            String header = sreq.getServletRequest().getHeader("Authorization");
//            log.debug("authHeader: {}", header);
//
//            if (header != null && header.startsWith("Bearer ")) {
//                String token = header.substring(7);
//                jwtUtil.validateToken(token); // throws if invalid
//                attrs.put("clientId", jwtUtil.extractClientId(token));
//                log.info("✅ WebSocket JWT valid, client: {}", attrs.get("clientId"));
//                return true;
//            }
//            log.warn("⚠️ Missing or invalid Authorization header");
//        }
//        res.setStatusCode(HttpStatus.UNAUTHORIZED);
//        return false;
//    }

    @Override public void afterHandshake(ServerHttpRequest req, ServerHttpResponse res,
                                         WebSocketHandler handler, Exception ex) {}
}



