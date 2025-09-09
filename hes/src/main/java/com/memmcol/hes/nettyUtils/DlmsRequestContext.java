package com.memmcol.hes.nettyUtils;

import io.netty.channel.Channel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.UUID;


@Data
public class DlmsRequestContext {
    private final long expiryTime;
    private final String MeterId;
    private final String correlationId; // UUID or time-based unique key
    private final long requestStartTime;
//    private final long deadlineTimeMs;
    private final Channel channel;
    @Getter
    private boolean timedOut = false;

    public DlmsRequestContext(String meterSerial, Channel channel, long timeoutMs) {

        this.MeterId = meterSerial;
        this.channel = channel;
        this.correlationId = meterSerial + "-" + System.nanoTime(); //UUID.randomUUID().toString(); // could be meterSerial + timestamp
        this.requestStartTime = System.currentTimeMillis();
        this.expiryTime = this.requestStartTime + timeoutMs;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiryTime;
    }

    /**
     * Total duration since request start (ms).
     */
    public long getDuration() {
        return System.currentTimeMillis() - requestStartTime;
    }

    public void markTimeout() {
        this.timedOut = true;
    }

    /**
     * How much extra time (ms) passed beyond expiry.
     * Returns 0 if still within expiry window.
     */
    public long getOverdueDelay() {
        long now = System.currentTimeMillis();
        return (now > expiryTime) ? (now - expiryTime) : 0L;
    }


}
