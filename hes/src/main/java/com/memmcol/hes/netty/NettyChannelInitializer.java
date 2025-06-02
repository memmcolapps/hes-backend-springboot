package com.memmcol.hes.netty;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class NettyChannelInitializer extends ChannelInitializer<SocketChannel> {

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();

        // Optional: idle handler to close inactive meters
        pipeline.addLast("IdleStateHandler", new IdleStateHandler(300, 0, 0, TimeUnit.SECONDS));

        // Decoder, Encoder (Optional, depending on meter protocol framing)
        pipeline.addLast("dlmsDecoder", new DLMSFrameDecoder());
        pipeline.addLast("dlmsEncoder", new DLMSFrameEncoder());

        // Business logic
        pipeline.addLast("dlmsHandler", new DLMSMeterHandler());
    }

}
