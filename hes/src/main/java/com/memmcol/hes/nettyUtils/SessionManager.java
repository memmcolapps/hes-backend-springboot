package com.memmcol.hes.nettyUtils;

import com.memmcol.hes.application.port.out.TxRxService;
import com.memmcol.hes.service.MeterConnections;
import com.memmcol.hes.service.MeterSession;
import gurux.dlms.GXByteBuffer;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.enums.Authentication;
import gurux.dlms.enums.InterfaceType;
import gurux.dlms.internal.GXCommon;
import io.netty.channel.Channel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.memmcol.hes.nettyUtils.RequestResponseService.*;

@Service
@Slf4j
public class SessionManager {
    private final Map<String, MeterSession> sessionMap = new ConcurrentHashMap<>();
    // Session timeout for inactive meters (e.g., 5 minutes)
    private final Duration SESSION_TIMEOUT = Duration.ofMinutes(3);
    private final TxRxService txRxService;

    public SessionManager(TxRxService txRxService) {
        this.txRxService = txRxService;
    }

//    @PostConstruct
//    public void init() {
//        cleanInflightRequests();
////        monitorInactivity();
//    }

    /**
     * Adds a new session if one doesn't already exist.
     */
    public synchronized void addSession(String serial, Channel channel) throws Exception {
        if (channel == null) {
            log.info("No channel provided for session with meter {}", serial);
        }
        MeterSession existing = sessionMap.get(serial);
        if (existing != null && existing.isAssociated()) {
            log.debug("Session already exists and is associated: {}", serial);
            return;
        }
        GXDLMSClient dlmsClient = new GXDLMSClient(
                true, 1, 1,
                Authentication.LOW,
                "12345678",
                InterfaceType.WRAPPER);
        try {
            String msg = String.format("Setting up DLMS Association for meter=%s", serial);
            log.info(msg);
            logTx(serial, msg);
            byte[][] aarq = dlmsClient.aarqRequest();
            byte[] response = txRxService.sendReceiveWithContext(serial, aarq[0], 20000);
            byte[] payload = Arrays.copyOfRange(response, 8, response.length);
            GXByteBuffer replyBuffer = new GXByteBuffer(payload);
            try {
                dlmsClient.parseAareResponse(replyBuffer);
            } catch (IllegalArgumentException e) {
                log.warn("⚠️ AARE parse failed: {}", e.getMessage());
            }
            log.info("✅ DLMS Association established for {}", serial);
            MeterSession meterSession = new MeterSession(serial, channel, dlmsClient, txRxService);
            meterSession.setAssociated(true);
            sessionMap.put(serial, meterSession);
        } catch (Exception e) {
             log.error("❌ DLMS Association failed for {}: {}", serial, e.getMessage());
            throw e;
        }
    }

    /**
     * Get the associated client for reading.
     * Also, update last meter session (To get when last TX or RX was made)
     */
    public GXDLMSClient getClient(String serial) {
        MeterSession session = sessionMap.get(serial);
        if (session != null && session.isAssociated()) {
            return session.getClient(); // Updates last used time
        }
        return null;
    }

    public GXDLMSClient getOrCreateClient(String serial) throws Exception {
        GXDLMSClient client = getClient(serial);
        if (client == null) {
            addSession(serial, MeterConnections.getChannel(serial));
            client = getClient(serial);
        }
        return client;
    }

    /**
     * Optional: forcibly clear session if needed.
     */
    public void removeSession(String serial) {
        sessionMap.remove(serial);
    }

    public boolean isAssociationLost(byte[] response) {
        // Match DLMS "Association Lost" signature
        if (response == null || response.length < 3) return false;
        // Check specific sequence or code e.g., ends with D8 01 01
        int len = response.length;
        return response[len - 3] == (byte) 0xD8
                && response[len - 2] == 0x01
                && response[len - 1] == 0x01;
    }

//    public void cleanInflightRequests() {
//        ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();
//        cleaner.scheduleAtFixedRate(() -> {
//            long now = System.currentTimeMillis();
//            inflightRequests.entrySet().removeIf(entry -> {
//                DlmsRequestContext ctx = entry.getValue();
//                if (now > ctx.getExpiryTime()) {
//                    log.warn("🧹 Cleaning up expired request: CID={}, Meter={}", entry.getKey(), ctx.getMeterId());
//                    TRACKER.remove(entry.getKey());
//                    return true;
//                }
//                return false;
//            });
//        }, 30, 30, TimeUnit.SECONDS); // every 30s
//    }


    /**
     * Remove expired sessions (run on a schedule).
     * Remove Cleaning expired TX Correlation ID
     */
//    @Scheduled(fixedDelay = 30000) // every 30 seconds
    public void cleanupExpiredEntries() {
        log.debug("🔍 Starting cleanup: sessions and inflight requests...");

        // 1. Cleanup expired sessions and disconnect active meters
//        int sessionMapBefore = sessionMap.size();
//        sessionMap.entrySet().removeIf(entry -> {
//            MeterSession meterSession = entry.getValue();
//            if (meterSession.isExpired(SESSION_TIMEOUT)) {
//                try {
//                    meterSession.sendDisconnectRequest(meterSession);
//                    log.warn("⏳ Cleanup triggered disconnect for expired meter {}", entry.getKey());
//                } catch (Exception e) {
//                    log.error("❌ Failed to disconnect {} during cleanup: {}", entry.getKey(), e.getMessage());
//                }
//                return true; // remove it after disconnect
//            }
//            return false;
//        });
//        int sessionMapAfter = sessionMap.size();
//        log.debug("🔍 Sessions cleaned: {} removed, {} remaining", sessionMapBefore - sessionMapAfter, sessionMapAfter);

        // 2. Cleanup expired inflight requests
//        int inflightBefore = inflightRequests.size();
//        long now = System.currentTimeMillis();
//        inflightRequests.entrySet().removeIf(entry -> {
//            DlmsRequestContext ctx = entry.getValue();
//            if (now > ctx.getExpiryTime()) {
//                log.warn("🧹 Cleaning expired TX: CID={}, MetersEntity={}", entry.getKey(), ctx.getMeterId());
//                TRACKER.remove(entry.getKey());
//                return true;
//            }
//            return false;
//        });
//        int inflightAfter = inflightRequests.size();
//        log.debug("🧹 Inflight TXs cleaned: {} removed, {} remaining", inflightBefore - inflightAfter, inflightAfter);
    }
}
