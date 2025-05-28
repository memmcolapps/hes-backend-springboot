package com.memmcol.hes.netty_v3;


import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NettyServer_v3 {

    private final int port;

    public NettyServer_v3(int port) {
        this.port = port;
    }

    public void start() throws InterruptedException {
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup workers = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(boss, workers)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new NettyServerInitializer_v3());

            ChannelFuture future = bootstrap.bind(port).sync();
            log.info("🚀 Netty server started on port " + port);
            future.channel().closeFuture().sync();

        } finally {
            boss.shutdownGracefully();
            workers.shutdownGracefully();
        }
    }
}
