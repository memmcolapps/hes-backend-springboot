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
public final class RequestResponseService {

    private static final Map<String, BlockingQueue<byte[]>> meterResponseQueues = new ConcurrentHashMap<>();
    private static final Map<String, String> commandKeyMap = new ConcurrentHashMap<>();

    public static void registerResponseQueue(String serial) {
        meterResponseQueues.put(serial, new LinkedBlockingQueue<>(1));
    }

    public static void unregisterResponseQueue(String serial) {
        meterResponseQueues.remove(serial);
    }

    public static byte[] sendCommand(String serial, byte[] command) {
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

    public static byte[] sendCommandWithRetry(String serial, byte[] command, int maxRetries, long delayMs) {
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
}
