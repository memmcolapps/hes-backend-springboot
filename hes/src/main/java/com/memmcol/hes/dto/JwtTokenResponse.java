package com.memmcol.hes.dto;

import lombok.Data;

@Data
public class JwtTokenResponse {
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    public String desc;

    public JwtTokenResponse( String accessToken, String refreshToken, Long expiresIn, String desc ) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.desc = desc;
    }
}
