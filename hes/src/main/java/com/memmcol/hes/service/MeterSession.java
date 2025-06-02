package com.memmcol.hes.service;

import gurux.dlms.GXDLMSClient;
import io.netty.channel.Channel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

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

}
