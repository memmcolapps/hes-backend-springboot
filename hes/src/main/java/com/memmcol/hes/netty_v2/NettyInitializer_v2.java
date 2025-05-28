package com.memmcol.hes.netty_v2;

import com.memmcol.hes.service.RequestResponseService;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class NettyInitializer_v2 extends ChannelInitializer<SocketChannel> {

    @Override
    protected void initChannel(SocketChannel ch) {

        RequestResponseService clientService = new RequestResponseService();
        log.info("Initializing new channel: {}", ch.remoteAddress());

        final EventExecutorGroup group = new DefaultEventExecutorGroup(100); //thread pool of 1500

        // Enable logging for better debugging
        ch.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG));
        //Byte encoder and decoder
        ch.pipeline().addLast("idleStateHandler", new IdleStateHandler(0, 0, 4, TimeUnit.MINUTES)); // https://netty.io/4.1/api/index.html?io/netty/handler/timeout/IdleStateHandler.html
        ch.pipeline().addLast(new ByteArrayDecoder()); // add without name, name auto generated
        ch.pipeline().addLast(new ByteArrayEncoder()); // add without name, name auto generated

        //===========================================================
        // 2. run handler with slow business logic
        //    in separate thread from I/O thread
        //===========================================================
        ch.pipeline().addLast(group, "serverHandler", new NettyServerHandler_v2(clientService));
    }
}