package com.memmcol.hes.service;

import gurux.dlms.GXByteBuffer;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.enums.Authentication;
import gurux.dlms.enums.InterfaceType;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SessionManager {
    private final Map<String, MeterSession> sessions = new ConcurrentHashMap<>();
    private GXDLMSClient dlmsclient = new GXDLMSClient();


    public void addSession(String serial, Channel channel) {
        if (!sessions.containsKey(serial)) {
            dlmsclient = new GXDLMSClient(
                    true,
                    1,
                    1,
                    Authentication.LOW,
                    "12345678",
                    InterfaceType.WRAPPER);
            try {
                byte[][] aarq = dlmsclient.aarqRequest();
                byte[] aare = RequestResponseService.sendCommand(serial, aarq[0]);
                dlmsclient.parseAareResponse(new GXByteBuffer(aare));

                // ✅ No exception: association succeeded
                MeterSession session = new MeterSession(serial, channel, dlmsclient);
                session.setAssociated(true);
                log.info("✅ Association established with meter: {}", serial);
                sessions.put(serial, session);
            } catch (Exception e) {
                log.error("❌ Association failed: {}", e.getMessage());
            }
        }
    }

    public Optional<MeterSession> getSession(String serial) {
        return Optional.ofNullable(sessions.get(serial));
    }

    public void removeSession(String serial) {
        sessions.remove(serial);
    }

    public void cleanupExpiredSessions(Duration timeout) {
        sessions.entrySet().removeIf(e -> e.getValue().isExpired(timeout));
    }
}
