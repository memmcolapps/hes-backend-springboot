package com.memmcol.hes.security;

import com.memmcol.hes.security.util.JwtUtil;
import io.jsonwebtoken.*;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {


    private final JwtUtil jwtUtil;

    @Autowired
    private AuthenticationEntryPoint authEntryPoint;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response); // Skip for non-authenticated endpoints
            return;
        }

        String token = authHeader.substring(7);

        try {
            jwtUtil.validateToken(token);
            String clientId = jwtUtil.extractClientId(token);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(clientId, null, Collections.emptyList());

            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            handleAuthError(request, response, "Token has expired", e);
        } catch (UnsupportedJwtException e) {
            handleAuthError(request, response, "Unsupported JWT", e);
        } catch (MalformedJwtException e) {
            handleAuthError(request, response, "Malformed JWT", e);
        } catch (SecurityException e) {
            handleAuthError(request, response, "Invalid JWT signature", e);
        } catch (IllegalArgumentException e) {
            handleAuthError(request, response, "JWT claims string is empty", e);
        } catch (JwtException e) {
            handleAuthError(request, response, "JWT error: " + e.getMessage(), e);
        }
    }

    private void handleAuthError(HttpServletRequest request,
                                 HttpServletResponse response,
                                 String message,
                                 Exception ex) throws IOException, ServletException {
        request.setAttribute("auth_error", message);
        authEntryPoint.commence(request, response,
                new org.springframework.security.core.AuthenticationException(message, ex) {});
    }
}