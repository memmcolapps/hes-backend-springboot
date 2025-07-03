package com.memmcol.hes.netty;

import com.memmcol.hes.service.DlmsService;
import com.memmcol.hes.service.MeterStatusService;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class NettyChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final MeterStatusService meterStatusService;
    private final DlmsService dlmsService;

    @Autowired
    public NettyChannelInitializer(MeterStatusService meterStatusService, DlmsService dlmsService) {
        this.meterStatusService = meterStatusService;
        this.dlmsService = dlmsService;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();

        // Optional: idle handler to close inactive meters
        pipeline.addLast("IdleStateHandler", new IdleStateHandler(300, 0, 0, TimeUnit.SECONDS));

        // Decoder, Encoder (Optional, depending on meter protocol framing)
        pipeline.addLast("dlmsDecoder", new DLMSFrameDecoder());
        pipeline.addLast("dlmsEncoder", new DLMSFrameEncoder());

        // Business logic
        pipeline.addLast("dlmsHandler", new DLMSMeterHandler(meterStatusService, dlmsService));
    }

}
