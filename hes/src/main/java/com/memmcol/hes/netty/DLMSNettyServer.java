package com.memmcol.hes.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
public class DLMSNettyServer {

    private final int port;
    private final NettyChannelInitializer channelInitializer;

    public DLMSNettyServer(int port, NettyChannelInitializer channelInitializer) {
        this.port = port;
        this.channelInitializer = channelInitializer;
    }

    public void start() throws IOException, InterruptedException {
        // Use appropriate thread pool sizes based on your CPU cores
        EventLoopGroup bossGroup = new NioEventLoopGroup(1); // Accepts connections
        EventLoopGroup workerGroup = new NioEventLoopGroup(); // Handles I/O (default: #cores * 2)

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    //Set socket options for connection reliability:
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_RCVBUF, 1024 * 1024) //1048576
                    .childOption(ChannelOption.SO_SNDBUF, 1024 * 1024) //1048576
                    .childHandler(channelInitializer);
//                    .childHandler(new NettyChannelInitializer(channelInitializer));

            ChannelFuture future = bootstrap.bind(port).sync();
            log.info("✅ DLMS Netty Server started on port: {}",  port);

            future.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
