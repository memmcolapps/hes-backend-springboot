package com.memmcol.hes.netty_v2;

import com.memmcol.hes.netty.NettyInitializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NettyServer_v2 {

    private final NettyInitializer_v2 serverInitializer;
    @Value("${nettyserver.port}")
    private int port;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public NettyServer_v2(NettyInitializer_v2 serverInitializer) {
        this.serverInitializer = serverInitializer;
    }

//    @PostConstruct
//    public void init() throws Exception {
//        log.info("***** New ***** Starting Netty Server  ****  New ****");
//        this.start(port);
//    }
//    @PostConstruct
//    public void logPort() {
//        log.info("🛰 Spring Boot HTTP server initialized on port: {}", System.getProperty("server.port", "default (8080)"));
//        log.info("🛰 Spring Boot HTTP server initialized on port: {}", serverPort);
//    }

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
            log.error("Error starting New Netty server", e);
            Thread.currentThread().interrupt();
        }
    }

//    @PreDestroy
//    public void stop() {
//        log.info("Shutting down New Netty server...");
//        bossGroup.shutdownGracefully();
//        workerGroup.shutdownGracefully();
//    }
}