package com.memmcol.hes.config;

import com.memmcol.hes.service.RequestResponseService;
import gurux.dlms.GXByteBuffer;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXDLMSSettings;
import gurux.dlms.enums.Authentication;
import gurux.dlms.enums.Conformance;
import gurux.dlms.enums.InterfaceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.EnumSet;

@Configuration
@Slf4j
public class DlmsConfig {

    @Bean
    public GXDLMSClient dlmsClient() {

        GXDLMSClient client = new GXDLMSClient(
                true,                    // Logical name referencing ✅
                1,                       // Client address (usually 1 for public)
                1,                       // Server address
                Authentication.LOW,     // Auth type
                "12345678",              // Password
                InterfaceType.WRAPPER    // DLMS WRAPPER mode
        );

//        client.setProposedConformance(EnumSet.of(
//                Conformance.ACTION,
//                Conformance.SELECTIVE_ACCESS,
//                Conformance.SET,
//                Conformance.GET,
//                Conformance.MULTIPLE_REFERENCES,
//                Conformance.BLOCK_TRANSFER_WITH_ACTION,
//                Conformance.BLOCK_TRANSFER_WITH_SET_OR_WRITE,
//                Conformance.BLOCK_TRANSFER_WITH_GET_OR_READ
//        ));
        return client;
    }
}
