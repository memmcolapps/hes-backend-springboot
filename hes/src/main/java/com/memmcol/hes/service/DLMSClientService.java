package com.memmcol.hes.service;

import gurux.dlms.GXByteBuffer;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXReplyData;
import gurux.dlms.enums.Authentication;
import gurux.dlms.enums.InterfaceType;
import gurux.dlms.internal.GXCommon;
import gurux.dlms.objects.GXDLMSClock;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Service
public class DLMSClientService {

    private static final Map<String, BlockingQueue<byte[]>> meterResponseQueues = new ConcurrentHashMap<>();
    private static final Map<String, String> commandKeyMap = new ConcurrentHashMap<>();

    public static void registerResponseQueue(String serial) {
        meterResponseQueues.put(serial, new LinkedBlockingQueue<>(1));
    }

    public static void unregisterResponseQueue(String serial) {
        meterResponseQueues.remove(serial);
    }

    private byte[] sendCommand(String serial, byte[] command) {
        Channel channel = MeterConnections.getChannel(serial);
        if (channel != null && channel.isActive()) {
            String reqKey = DLMSRequestTracker.register(serial);
            commandKeyMap.put(serial, reqKey); // Track last request

            channel.writeAndFlush(Unpooled.wrappedBuffer(command));

            try {
                return DLMSRequestTracker.waitForResponse(reqKey, 3000); // 3s timeout
            } catch (TimeoutException e) {
                log.warn("DLMS read timeout: {}", e.getMessage());
                throw new IllegalStateException(e);
            }
        } else {
            throw new IllegalStateException("Inactive or missing channel for " + serial);
        }
    }

    public byte[] sendCommandWithRetry(String serial, byte[] command, int maxRetries, long delayMs) {
        int attempt = 0;

        while (attempt < maxRetries) {
            try {
                return sendCommand(serial, command); // Uses the async tracker from before
            } catch (Exception ex) {
                log.warn("Attempt {} failed for {}: {}", attempt + 1, serial, ex.getMessage());
                attempt++;
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during DLMS retry delay");
                }
            }
        }

        throw new IllegalStateException("DLMS command failed after " + maxRetries + " retries");
    }


    public static BlockingQueue<byte[]> getQueue(String serial) {
        return meterResponseQueues.get(serial);
    }

    public static String getLastRequestKey(String serial) {
        return commandKeyMap.get(serial);
    }


    //Setup Gurux DLMS Client
    /*
            } else if (modelNo.equalsIgnoreCase("MMX-313-CT")) {
            if (doDEBUG) {
                DEBUG_LOGGER.info("Executing WRAPPER interface type for MMX-313-CT Meter");
            }
            settings.client.setInterfaceType(InterfaceType.WRAPPER);
            settings.client.setAuthentication(Authentication.valueOfString("Low"));
            settings.client.setPassword("12345678");
            settings.client.setUseLogicalNameReferencing(Boolean.TRUE);
//            settings.client.setServerAddressSize(28672);
            settings.client.setServerAddress(1);
            settings.client.setClientAddress(1);
     */
    public void readClock(String serial) throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, SignatureException, InvalidKeyException {

//        GXDLMSClient client = new GXDLMSClient(
//                true, // useLogicalNameReferencing
//                0x01, // client address (logical)
//                0x01,    // server address (physical/short)
//                Authentication.LOW,
//                "12345678",
//                InterfaceType.WRAPPER
//        );

        //Setup Gurux DLMS Client
        GXDLMSClient client = new GXDLMSClient();
        client.setInterfaceType(InterfaceType.WRAPPER);
        client.setClientAddress(1);
        client.setServerAddress(1);
        client.setAuthentication(Authentication.valueOfString("Low"));
        client.setPassword("12345678");
        client.setInterfaceType(InterfaceType.WRAPPER);

        //2. Generate AARQ Frame
        byte[][] aarq = client.aarqRequest();
        log.info("AARQ (hex): {}", GXCommon.toHex(aarq[0]));

        //Send to meter
        byte[] response = sendCommand(serial, aarq[0]);
        GXByteBuffer reply = new GXByteBuffer(response);

        //3. Parse AARE Response from Meter
        client.parseAareResponse(reply); // This validates acceptance
        log.info("🔓 Session Established: OK");

        //4. Build GET Frame for OBIS Clock (`0.0.1.0.0.255`)
        GXDLMSClock clock = new GXDLMSClock("0.0.1.0.0.255");
        // Attribute 2 = time
        byte[][] readClockRequest = client.read(clock, 2);
        //Generate Clock frame
        for (byte[] frame : readClockRequest) {
            log.info("GET Clock Frame: {}", GXCommon.toHex(frame));
        }

        //5. Parse Clock GET.response
        GXReplyData replyClock = new GXReplyData();
        byte[] responseClock = sendCommand(serial, readClockRequest[0]);
        boolean Hasdata = client.getData(responseClock, replyClock, null);

        Object result = replyClock.getValue();
        log.info("🕒 Clock Read: {}",  result);

        //If the value is a `GXDateTime`, it will print the actual date/time.

        //6. Generate Disconnect Frame
        byte[] disconnectFrame = client.disconnectRequest();
        log.info("Disconnect Frame: {}", GXCommon.toHex(disconnectFrame));
        sendCommand(serial, disconnectFrame);

//        Send this to close the association cleanly.

    }


}
