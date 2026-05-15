package com.memmcol.hes.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DlmsReadResponse {

//    @JsonProperty("raw_response_hex")
//    private String rawResponseHex;

    @JsonProperty("error_code")
    private int errorCode;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("value")
    private Object value;

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("timestamp")
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    public static DlmsReadResponse success(String rawHex, Object value) {
        return DlmsReadResponse.builder()
//                .rawResponseHex(rawHex)
                .errorCode(0)
                .errorMessage(null)
                .value(value)
                .success(true)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static DlmsReadResponse error(int errorCode, String errorMessage, String rawHex) {
        return DlmsReadResponse.builder()
//                .rawResponseHex(rawHex)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .value(null)
                .success(false)
                .timestamp(LocalDateTime.now())
                .build();
    }
}