package com.memmcol.hes.netty;

import com.memmcol.hes.service.MeterConnections;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Slf4j
public class NettyServerHolder {
//    private static final Logger log = LoggerFactory.getLogger(NettyServerHolder.class);
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private static Channel channel;

    private boolean running = false;

    public void startServer(int port, ChannelInitializer<SocketChannel> initializer) throws InterruptedException {

        if (running) return;

        // Use appropriate thread pool sizes based on your CPU cores
        bossGroup = new NioEventLoopGroup(1); // Accepts connections
        workerGroup = new NioEventLoopGroup(); // Handles I/O (default: #cores * 2)

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                //Set socket options for connection reliability:
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_RCVBUF, 1024 * 1024)
                .childOption(ChannelOption.SO_SNDBUF, 1024 * 1024)
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
