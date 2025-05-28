package com.memmcol.hes.netty_v3;

import com.memmcol.hes.service.RequestResponseService;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import org.springframework.stereotype.Component;

@Component
public class NettyServerInitializer_v3 extends ChannelInitializer<SocketChannel> {

    RequestResponseService clientService = new RequestResponseService();



    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new ByteArrayDecoder());
        pipeline.addLast(new ByteArrayEncoder());
        pipeline.addLast(new NettyServerHandler_v3(clientService)); // Your byte[] handler
    }
}

