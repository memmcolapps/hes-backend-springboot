package com.memmcol.hes.service;

import com.memmcol.hes.nettyUtils.RequestResponseService;
import com.memmcol.hes.nettyUtils.SessionManager;
import gurux.dlms.GXDLMSClient;
import io.netty.channel.Channel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;

/*
This class holds:

The GXDLMSClient instance
The Netty channel
A timestamp for last activity
Flags for session state (e.g., association done)

 */
@Getter
@RequiredArgsConstructor
@Slf4j
public class MeterSession {
    private final String meterSerial;
    private final Channel channel;
    private final GXDLMSClient client;

    @Setter
    private boolean isAssociated = false;

    private Instant lastUsed = Instant.now();

    // Update lastUsed every time client is accessed
    public GXDLMSClient getClient() {
        this.lastUsed = Instant.now();
        return client;
    }
    // Check if the session has expired based on a timeout duration
    public boolean isExpired(Duration timeout) {
        return Instant.now().isAfter(lastUsed.plus(timeout));
    }

    public void updateLastUsed() {
        this.lastUsed = Instant.now();
    }

    public void sendDisconnectRequest(MeterSession meterSession) {

        GXDLMSClient client = meterSession.getClient();
        String meterSerial = meterSession.getMeterSerial();
        log.info("📴 Sending disconnect request to meter {}", meterSerial);
        // e.g., send via channel or command interface
        try {
            byte[] disconnect = client.disconnectRequest();
            byte[] response = RequestResponseService.sendReceiveWithContext(meterSerial, disconnect, 20000);
            log.info("Meter disconnected successfully.");
        } catch (Exception e) {
            log.warn("⚠️ Failed to send disconnect for {}: {}", meterSerial, e.getMessage());
        }
    }
}
