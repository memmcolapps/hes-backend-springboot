package com.memmcol.hes.service;

import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public final class MeterConnections {

    private static final ConcurrentHashMap<Channel, String> CHANNEL_TO_SERIAL_meterConnectionsPool = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Channel> SERIAL_TO_CHANNEL_meterConnectionsPool = new ConcurrentHashMap<>();

    private MeterConnections() {}

    public static void bind(Channel channel, String serial) {
        CHANNEL_TO_SERIAL_meterConnectionsPool.put(channel, serial);
        SERIAL_TO_CHANNEL_meterConnectionsPool.put(serial, channel);
        log.debug("🔗 Binding channel {} to serial {}", channel.id(), serial);

        log.debug("🔍 Looking for serial '{}'", serial);
        log.debug("🔍 Current serials: {}", SERIAL_TO_CHANNEL_meterConnectionsPool.keySet());
    }

    public static String getSerial(Channel channel) {
        return CHANNEL_TO_SERIAL_meterConnectionsPool.get(channel);
    }

    public static Channel getChannel(String serial) {
//        Channel channel = SERIAL_TO_CHANNEL_meterConnectionsPool.get(serial);
//        return channel;

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

    public static void remove(Channel channel) {
        String serial = CHANNEL_TO_SERIAL_meterConnectionsPool.remove(channel);
        if (serial != null) SERIAL_TO_CHANNEL_meterConnectionsPool.remove(serial);
        channel.close();
        log.info("❌ Disconnected Meter and connection {}", serial);
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
