package com.memmcol.hes.config;

import gurux.dlms.GXDLMSClient;
import gurux.dlms.enums.Authentication;
import gurux.dlms.enums.InterfaceType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DlmsConfig {

    @Bean
    public GXDLMSClient dlmsClient() {
        GXDLMSClient client = new GXDLMSClient();
        client.setInterfaceType(InterfaceType.WRAPPER);
        client.setClientAddress(1);
        client.setServerAddress(1);
        client.setAuthentication(Authentication.valueOfString("Low"));
        client.setPassword("12345678");
        client.setInterfaceType(InterfaceType.WRAPPER);

        return client;
    }
}
