package com.memmcol.hes.netty_v3;

import com.memmcol.hes.service.MeterConnections;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class NettyServerHolder {
    private static final Logger log = LoggerFactory.getLogger(NettyServerHolder.class);
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private static Channel channel;

    private boolean running = false;

    public void startServer(int port, ChannelInitializer<SocketChannel> initializer) throws InterruptedException {

        if (running) return;

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(initializer);

        ChannelFuture future = bootstrap.bind(port).sync();
        channel = future.channel();
        running = true;
        log.info("🚀 Netty DLMS server started on port " + port);
    }

    public void shutdown() {
        if (channel != null) {
            channel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        running = false;
        log.info("🛑 Netty DLMS server shutdown complete.");
    }

    public static boolean isRunning() {
        return channel != null && channel.isActive();
    }

    public void restart(int port, ChannelInitializer<SocketChannel> initializer) {
        shutdown();
        try {
            startServer(port, initializer);
        } catch (InterruptedException e) {
            System.err.println("❌ Restart failed: " + e.getMessage());
        }
    }

    public int getActiveMeterCount() {
        return MeterConnections.size(); // SessionManager must track connections
    }

    public Set<String> getActiveMeters() {
        return MeterConnections.getAllActiveSerials();
    }
}
