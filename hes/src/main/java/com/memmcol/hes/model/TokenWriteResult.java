package com.memmcol.hes.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenWriteResult {
    private TokenStatus tokenStatus;
    private int tokenResultCode;
    private String tokenStatusLabel;
    private BigDecimal meterCredit;
    private String logoutToken;
    private String rawHex;
    private boolean success;
    private String errorDetail;
}