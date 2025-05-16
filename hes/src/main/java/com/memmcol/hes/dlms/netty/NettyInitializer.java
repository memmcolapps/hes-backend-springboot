package com.memmcol.hes.dlms.netty;

import com.memmcol.hes.dlms.handler.NettyServerHandler;

import com.memmcol.hes.util.DLMSRequestBuilder;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NettyInitializer extends ChannelInitializer<SocketChannel> {

    private final DLMSRequestBuilder requestBuilder;

    @Override
    protected void initChannel(SocketChannel ch) {
        log.info("Initializing new channel: {}", ch.remoteAddress());

        // Enable logging for better debugging
        ch.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG));

        // Attach the Netty handler
        ch.pipeline().addLast(new NettyServerHandler());
    }
}