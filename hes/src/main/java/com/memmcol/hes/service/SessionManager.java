package com.memmcol.hes.service;

import gurux.dlms.GXByteBuffer;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.enums.Authentication;
import gurux.dlms.enums.InterfaceType;
import gurux.dlms.internal.GXCommon;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SessionManager {
    private final Map<String, MeterSession> sessions = new ConcurrentHashMap<>();
    // Session timeout for inactive meters (e.g., 5 minutes)
    private final Duration SESSION_TIMEOUT = Duration.ofMinutes(5);

    /**
     * Adds a new session if one doesn't already exist.
     */
    public synchronized void addSession(String serial, Channel channel) {
        if (channel == null) {
            log.info("No channel provided for session with meter {}", serial);
        }
        MeterSession existing = sessions.get(serial);
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
            byte[][] aarq = dlmsClient.aarqRequest();
            log.debug("AARQ (hex): {}", GXCommon.toHex(aarq[0]));

            byte[] response = RequestResponseService.sendCommand(serial, aarq[0]);
            byte[] payload = Arrays.copyOfRange(response, 8, response.length);
            GXByteBuffer replyBuffer = new GXByteBuffer(payload);

            try {
                dlmsClient.parseAareResponse(replyBuffer);
            } catch (IllegalArgumentException e) {
                log.warn("⚠️ AARE parse failed: {}", e.getMessage());
                log.debug("Assuming AARQ accepted externally.");
            }
            log.info("✅ DLMS Association established for {}", serial);
            MeterSession session = new MeterSession(serial, channel, dlmsClient);
            session.setAssociated(true);
            sessions.put(serial, session);

        } catch (Exception e) {
            log.error("❌ DLMS Association failed for {}: {}", serial, e.getMessage());
        }
    }

    /**
     * Get the associated client for reading.
     */
    public GXDLMSClient getClient(String serial) {
        MeterSession session = sessions.get(serial);
        if (session != null && session.isAssociated()) {
            return session.getClient(); // Updates last used time
        }
        return null;
    }

    public GXDLMSClient getOrCreateClient(String serial) {
        GXDLMSClient client = getClient(serial);
        if (client == null) {
            addSession(serial, MeterConnections.getChannel(serial));
            client = getClient(serial);
        }
        return client;
    }

    /**
     * Remove expired sessions (run on a schedule).
     */
    @Scheduled(fixedDelay = 60000)
    public void cleanupExpiredSessions() {
        log.debug("🔍 Cleaning up expired sessions...");
        int before = sessions.size();
        sessions.entrySet().removeIf(entry -> entry.getValue().isExpired(SESSION_TIMEOUT));
        int after = sessions.size();
        log.debug("🔍 Cleaned up expired sessions: {} removed, {} remaining", before - after, after);
    }

    /**
     * Optional: forcibly clear session if needed.
     */
    public void removeSession(String serial) {
        sessions.remove(serial);
    }


}
