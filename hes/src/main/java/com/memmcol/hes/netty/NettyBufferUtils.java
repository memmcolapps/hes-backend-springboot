package com.memmcol.hes.netty;

import com.memmcol.hes.nettyUtils.DLMSRequestTracker;
import com.memmcol.hes.service.MeterConnections;
import gurux.dlms.internal.GXCommon;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

import java.util.Queue;

@Slf4j
public class NettyBufferUtils {

    /**
     * Flush any inbound frames from the channel.
     * Optionally return the last frame if it matches expected key.
     */
    public static byte[] flushInbound(Channel channel, String expectedKey) {
        Queue<byte[]> inboundQueue = MeterConnections.getInboundQueue(channel);
        byte[] lastValid = null;

        while (!inboundQueue.isEmpty()) {
            byte[] frame = inboundQueue.poll();
            if (DLMSRequestTracker.matchesKey(frame, expectedKey)) {
                lastValid = frame;
            } else {
                log.debug("Discarding stale frame: {}", GXCommon.toHex(frame));
            }
        }
        return lastValid;
    }

    public static byte[] flushInbound(Channel channel) {
        Queue<byte[]> queue = MeterConnections.getInboundQueue(channel);
        if (queue == null || queue.isEmpty()) {
            log.debug("No stale inbound frames to flush for {}", channel.id());
            return null;
        }

        byte[] lastFrame = null;
        while (!queue.isEmpty()) {
            lastFrame = queue.poll(); // keep the last one
        }
        log.debug("Flushed inbound queue for {} (lastFrame length={})", channel.id(),
                (lastFrame != null ? lastFrame.length : 0));
        return lastFrame;
    }
}
