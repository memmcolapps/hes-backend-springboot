package com.memmcol.hes.config;

import gurux.dlms.GXDLMSClient;
import gurux.dlms.enums.Authentication;
import gurux.dlms.enums.InterfaceType;

public class DlmsClientConfig {

    public static GXDLMSClient configureClient() {

        // Initialize DLMS client for Wrapper mode
        GXDLMSClient client = new GXDLMSClient(
                true,
                17,
                1,
                Authentication.LOW,
                "00000000",
                InterfaceType.WRAPPER
        );

        return client;
    }
}