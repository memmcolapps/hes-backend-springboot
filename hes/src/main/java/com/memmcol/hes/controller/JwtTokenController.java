package com.memmcol.hes.controller;

import com.memmcol.hes.dto.JwtTokenRequest;
import com.memmcol.hes.dto.JwtTokenResponse;
import com.memmcol.hes.repository.ClientRepository;
import com.memmcol.hes.security.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class JwtTokenController {
    @Autowired
    ClientRepository clientRepository;

    @Autowired
    JwtUtil jwtUtil;


    @PostMapping("/token")
    public ResponseEntity<JwtTokenResponse> issueToken(@RequestBody JwtTokenRequest request) {
        String clientId = request.clientId;
        String clientSecret = request.clientSecret;

        if (isValidClient(clientId, clientSecret)) {
            String accessToken = jwtUtil.generateAccessToken(clientId);
            String refreshToken = jwtUtil.generateRefreshToken(clientId);

            JwtTokenResponse response = new JwtTokenResponse(
                    accessToken,
                    refreshToken,
                    jwtUtil.getAccessTokenExpiryInSeconds(),
                    "success"
            );

            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(401).body(new JwtTokenResponse("N/A", "N/A", null, "fail"));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshAccessToken(@RequestParam String refreshToken) {
        jwtUtil.validateToken(refreshToken); // throws if invalid or expired
        String clientId = jwtUtil.extractClientId(refreshToken);
        String newAccessToken = jwtUtil.generateAccessToken(clientId);

        return ResponseEntity.ok(Map.of(
                "accessToken", newAccessToken,
                "expiresIn", jwtUtil.getAccessTokenExpiryInSeconds()
        ));
    }

    private boolean isValidClient(String clientIdStr, String clientSecret) {
        try {
            UUID clientId = UUID.fromString(clientIdStr);
            return clientRepository.findByClientIdAndClientSecretAndStatus(
                            clientId, clientSecret, "ACTIVE")
                    .isPresent();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
