package com.memmcol.ami.auth.dto;

import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

public class AuthenticationResult {

    @Getter
    private UUID userId;
    @Getter
    private UUID orgId;
    @Getter
    private String email;

    @Getter
    private boolean authenticated;
    @Getter
    private String failureReason;

    private OffsetDateTime authenticatedAt;

    public static AuthenticationResult success(UUID userId, UUID orgId, String email) {
        AuthenticationResult result = new AuthenticationResult();
        result.userId = userId;
        result.orgId = orgId;
        result.email = email;
        result.authenticated = true;
        result.authenticatedAt = OffsetDateTime.now();
        return result;
    }

    public static AuthenticationResult failure(String reason) {
        AuthenticationResult result = new AuthenticationResult();
        result.authenticated = false;
        result.failureReason = reason;
        return result;
    }

}