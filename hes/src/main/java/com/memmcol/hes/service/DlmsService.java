package com.memmcol.hes.service;

import gurux.dlms.GXByteBuffer;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXReplyData;
import gurux.dlms.internal.GXCommon;
import gurux.dlms.objects.GXDLMSClock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.function.EntityResponse;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;

@Service
@Slf4j
public class DlmsService {
    private final GXDLMSClient dlmsClient;

    public DlmsService(GXDLMSClient dlmsClient) {
        this.dlmsClient = dlmsClient;
    }

    public String readClock(String serial) throws InvalidAlgorithmParameterException,
            NoSuchPaddingException, IllegalBlockSizeException,
            NoSuchAlgorithmException, BadPaddingException,
            SignatureException, InvalidKeyException {

        //2. Generate AARQ Frame
        byte[][] aarq = dlmsClient.aarqRequest();
        log.info("AARQ (hex): {}", GXCommon.toHex(aarq[0]));

        //Send to meter
        byte[] response = RequestResponseService.sendCommandWithRetry(serial, aarq[0], 20, 3000);
        GXByteBuffer reply = new GXByteBuffer(response);

        //3. Parse AARE Response from Meter
        dlmsClient.parseAareResponse(reply); // This validates acceptance
        log.info("🔓 Session Established: OK");

        //4. Build GET Frame for OBIS Clock (`0.0.1.0.0.255`)
        GXDLMSClock clock = new GXDLMSClock("0.0.1.0.0.255");
        // Attribute 2 = time
        byte[][] readClockRequest = dlmsClient.read(clock, 2);
        //Generate Clock frame
        for (byte[] frame : readClockRequest) {
            log.info("GET Clock Frame: {}", GXCommon.toHex(frame));
        }

        //5. Parse Clock GET.response
        GXReplyData replyClock = new GXReplyData();
        byte[] responseClock = RequestResponseService.sendCommandWithRetry(serial, readClockRequest[0], 20, 3000);
        boolean Hasdata = dlmsClient.getData(responseClock, replyClock, null);

        Object result = replyClock.getValue();
        log.info("🕒 Clock Read: {}",  result);

        //If the value is a `GXDateTime`, it will print the actual date/time.

        //6. Generate Disconnect Frame
        byte[] disconnectFrame = dlmsClient.disconnectRequest();
        log.info("Disconnect Frame: {}", GXCommon.toHex(disconnectFrame));
        RequestResponseService.sendCommandWithRetry(serial, disconnectFrame, 20, 3000);

//        Send this to close the association cleanly.

        return result.toString();
    }

    public String greet(String name){
        return "Al-hamdulilah. My first springboot DLMS application!. You are welcome, " + name + ".";
    }
}
