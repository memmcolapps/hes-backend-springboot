package com.memmcol.hes.netty;

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
    private final NettyChannelInitializer initializer;

    public NettyServerLauncher(NettyServerHolder holder, NettyChannelInitializer initializer) {
        this.holder = holder;
        this.initializer = initializer;
    }

    @PostConstruct
    public void startNettyServer() {
        new Thread(() -> {
            try {
                holder.startServer(port, initializer);
            } catch (Exception e) {
                log.error("❌ Failed to start Netty: {}",  e.getMessage());
            }
        }).start();
    }

    @PreDestroy
    public void shutdownNetty() {
        holder.shutdown();
    }
}
