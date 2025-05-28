package com.memmcol.hes.netty_v3;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NettyServerLauncher {

    @Value("${nettyserver.port}")
    private int port;

    private final NettyServerHolder holder;
    private final NettyServerInitializer_v3 initializer;

    public NettyServerLauncher(NettyServerHolder holder, NettyServerInitializer_v3 initializer) {
        this.holder = holder;
        this.initializer = initializer;
    }

    @PostConstruct
    public void startNettyServer() {
        new Thread(() -> {
            try {
//                new NettyServer_v3(port).start();
                holder.startServer(port, initializer);
            } catch (Exception e) {
                System.err.println("❌ Failed to start Netty: " + e.getMessage());
            }
        }).start();
    }

    @PreDestroy
    public void shutdownNetty() {
        holder.shutdown();
    }
}
