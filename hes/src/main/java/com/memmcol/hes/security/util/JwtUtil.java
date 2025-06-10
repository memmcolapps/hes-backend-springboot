package com.memmcol.hes.security.util;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    private final Key secretKey;

    public JwtUtil(@Value("${hes.token.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Value("$hes.token.issuer}")
    private String issuer;

    @Value("${hes.token.access.validity}")
    private long accessTokenValidity;

    @Value("${hes.token.refresh.validity}")
    private long refreshTokenValidity ;

   public String generateAccessToken(String clientId) {
        return Jwts.builder()
                .setSubject(clientId)
                .setIssuer(issuer)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessTokenValidity))
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(String clientId) {
        return Jwts.builder()
                .setSubject(clientId)
                .setIssuer(issuer)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshTokenValidity))
                .signWith(secretKey)
                .compact();
    }

    public void validateToken(String token) {
        Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token);
    }

    public String extractClientId(String token) {
        return Jwts.parserBuilder().setSigningKey(secretKey).build()
                .parseClaimsJws(token).getBody().getSubject();
    }

    public Date extractExpiry(String token) {
        return Jwts.parserBuilder().setSigningKey(secretKey).build()
                .parseClaimsJws(token).getBody().getExpiration();
    }

    public long getAccessTokenExpiryInSeconds() {
        return accessTokenValidity / 1000;
    }

    public long getRefreshTokenExpiryInSeconds() {
        return refreshTokenValidity / 1000;
    }


}