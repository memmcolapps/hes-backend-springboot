package com.memmcol.hes.dlms.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

@Slf4j
@Component
public class NettyServer {

    private final NettyInitializer serverInitializer;
    @Value("${server.port}")
    private int port;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public NettyServer(NettyInitializer serverInitializer) {
        this.serverInitializer = serverInitializer;
    }

    @PostConstruct
    public void init() throws Exception {
        this.start(port);
    }

   public void start(int i) {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(serverInitializer);

            ChannelFuture future = bootstrap.bind(port).sync();
            log.info("Netty server started on port {}", port);
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            log.error("Error starting Netty server", e);
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    public void stop() {
        log.info("Shutting down Netty server...");
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }
}