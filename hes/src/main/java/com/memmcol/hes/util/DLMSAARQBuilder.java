package com.memmcol.hes.util;

import com.memmcol.hes.config.DlmsClientConfig;
import com.memmcol.hes.dlms.service.DLMSDecoderService;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.enums.InterfaceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DLMSAARQBuilder {
    private static final Logger logger = LoggerFactory.getLogger(DLMSDecoderService.class);
    private static final int CLIENT_ADDRESS = 17;
    private static final int SERVER_ADDRESS = 1;
    private static final InterfaceType INTERFACE_TYPE = InterfaceType.WRAPPER;

    public static byte[][] buildAARQ(String password) {
        try{
            GXDLMSClient client = DlmsClientConfig.configureClient();

            // Generate the AARQ request
            return client.getApplicationAssociationRequest();
        }catch(Exception e){
            logger.error("Error Building AARQ request: ", e.getMessage());
        }

        return new byte[0][];
    }
}