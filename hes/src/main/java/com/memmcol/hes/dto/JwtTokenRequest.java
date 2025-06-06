package com.memmcol.hes.dto;

public class JwtTokenRequest {
    public String clientId;
    public String clientSecret;

    public String getClientId() {
        return clientId;
    }

    public void setClientId( String clientId ) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret( String clientSecret ) {
        this.clientSecret = clientSecret;
    }
}