package com.memmcol.hes.service;

import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
public final class MeterConnections {

    private static final ConcurrentHashMap<Channel, String> CHANNEL_TO_SERIAL_meterConnectionsPool = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Channel> SERIAL_TO_CHANNEL_meterConnectionsPool = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Channel, Queue<byte[]>> CHANNEL_INBOUND_QUEUES = new ConcurrentHashMap<>();


    private MeterConnections() {}

    public static void bind(Channel channel, String serial) {
        Channel previous = SERIAL_TO_CHANNEL_meterConnectionsPool.put(serial, channel);
        CHANNEL_TO_SERIAL_meterConnectionsPool.put(channel, serial);
        CHANNEL_INBOUND_QUEUES.put(channel, new ConcurrentLinkedQueue<>());
        log.debug("🔗 Binding channel {} to serial {}", channel.id(), serial);

        // A meter re-connected on a new socket before the old one was torn down.
        // Drop the stale channel so its later disconnect cannot emit a phantom
        // OFFLINE for this meter, which is now live on the new channel.
        if (previous != null && previous != channel) {
            CHANNEL_TO_SERIAL_meterConnectionsPool.remove(previous);
            CHANNEL_INBOUND_QUEUES.remove(previous);
            previous.close();
            log.info("🔁 Serial {} rebound to channel {}; closed stale channel {}",
                    serial, channel.id(), previous.id());
        }
    }

    public static String getSerial(Channel channel) {
        return CHANNEL_TO_SERIAL_meterConnectionsPool.get(channel);
    }

    public static Channel getChannel(String serial) {
        log.debug("📡 Looking up channel for serial: '{}'", serial);
        Channel ch = SERIAL_TO_CHANNEL_meterConnectionsPool.get(serial);

        if (ch == null) {
            log.warn("❌ No channel found for serial: '{}'", serial);
            log.debug("🔍 Available serials: {}", SERIAL_TO_CHANNEL_meterConnectionsPool.keySet());
        } else {
            log.debug("✅ Found channel {} for serial {}", ch.id(), serial);
        }

        return ch;
    }

    public static Queue<byte[]> getInboundQueue(Channel channel) {
        return CHANNEL_INBOUND_QUEUES.computeIfAbsent(channel, k -> new ConcurrentLinkedQueue<>());
    }

    public static void remove(Channel channel) {
        String serial = CHANNEL_TO_SERIAL_meterConnectionsPool.remove(channel);
        if (serial != null) {
            // Only clear the serial mapping if it still points to THIS channel.
            // If the meter already reconnected on a newer channel, keep that intact.
            SERIAL_TO_CHANNEL_meterConnectionsPool.remove(serial, channel);
        }
        CHANNEL_INBOUND_QUEUES.remove(channel);
        channel.close();
        log.info("❌ Disconnected Meter and connection {}", serial);
    }

    /**
     * True only if {@code serial} is currently bound to exactly {@code channel}.
     * Used to suppress OFFLINE events from stale channels after a reconnect.
     */
    public static boolean isCurrentChannel(String serial, Channel channel) {
        return serial != null && channel != null
                && SERIAL_TO_CHANNEL_meterConnectionsPool.get(serial) == channel;
    }

    public static boolean isActive(String serial) {
        Channel ch = getChannel(serial);
        return ch != null && ch.isActive();
    }

    public static Set<String> getAllActiveSerials() {
        return SERIAL_TO_CHANNEL_meterConnectionsPool.keySet();
    }

    public static int size() {
        return SERIAL_TO_CHANNEL_meterConnectionsPool.size();
    }

}
