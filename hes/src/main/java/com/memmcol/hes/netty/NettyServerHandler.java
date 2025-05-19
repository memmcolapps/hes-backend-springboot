package com.memmcol.hes.netty;

import gurux.dlms.GXDLMSClient;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class NettyServerHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private GXDLMSClient client;

    @Override
    protected void channelRead0( ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf ) throws Exception {

    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("New connection established: {}", ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("Connection closed: {}", ctx.channel().remoteAddress());
        try {
            if (client != null) {
                log.info("DLMS client disconnected");
                client = null;
            }
        } catch (Exception e) {
            log.warn("Failed to disconnect DLMS client", e);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Error in channel", cause);
        ctx.close();
    }
}