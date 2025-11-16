package com.memmcol.hes.netty;

import com.memmcol.hes.gridflex.sse.MeterHeartbeatService;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.nettyUtils.EventNotificationHandler;
import com.memmcol.hes.nettyUtils.MeterHeartbeatManager;
import com.memmcol.hes.service.DlmsService;
import com.memmcol.hes.service.MeterStatusService;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class NettyChannelInitializer extends ChannelInitializer<SocketChannel> {

//    private final MeterStatusService meterStatusService;
    private final DlmsReaderUtils dlmsReaderUtils;
//    private final MeterHeartbeatManager heartbeatManager;
    private final ScheduledExecutorService dlmsScheduledExecutor;
    private final EventNotificationHandler handler;
    private final MeterHeartbeatService heartbeatService;

    @Autowired
    public NettyChannelInitializer(DlmsReaderUtils dlmsReaderUtils,
//                                   MeterStatusService meterStatusService,
//                                   MeterHeartbeatManager heartbeatManager,
                                   @Qualifier("dlmsScheduledExecutor") ScheduledExecutorService dlmsScheduledExecutor,
                                   EventNotificationHandler handler,
                                   MeterHeartbeatService heartbeatService
    ) {
//        this.meterStatusService = meterStatusService;
        this.dlmsReaderUtils = dlmsReaderUtils;
//        this.heartbeatManager = heartbeatManager;
        this.dlmsScheduledExecutor = dlmsScheduledExecutor;
        this.handler = handler;
        this.heartbeatService = heartbeatService;
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
        pipeline.addLast("dlmsHandler", new DLMSMeterHandler(dlmsReaderUtils, dlmsScheduledExecutor, handler, heartbeatService));
    }

}
