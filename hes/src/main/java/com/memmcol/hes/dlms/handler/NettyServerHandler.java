package com.memmcol.hes.dlms.handler;

import com.memmcol.hes.dlms.service.DLMSDecoderService;
import com.memmcol.hes.util.DLMSAARQBuilder;
import com.memmcol.hes.util.HexUtils;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.enums.ObjectType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

import static com.memmcol.hes.config.DlmsClientConfig.configureClient;


@Slf4j
public class NettyServerHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private final DLMSDecoderService decoderService = new DLMSDecoderService();


    // Security settings
    private static final String LLS_PASSWORD = "00000000"; // Example password for LLS
    private static final boolean USE_WRAPPER = true; // Wrapper mode enabled

    private GXDLMSClient client;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("New connection established: {}", ctx.channel().remoteAddress());

        try {
            // ✅ Initialize DLMS Client
            client = configureClient();

            // ✅ Build and send AARQ request
            byte[][] aarqFrames = DLMSAARQBuilder.buildAARQ(LLS_PASSWORD);
            sendFrames(ctx, aarqFrames);

            // ✅ Schedule load profile request after login (5-second delay)
            ctx.executor().schedule(() -> sendTotalImportActiveEnergyRequest(ctx), 5, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.error("Failed to initialize/send AARQ request", e);
            ctx.close(); // Close connection if AARQ fails
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buffer) {
        byte[] receivedBytes = new byte[buffer.readableBytes()];
        buffer.readBytes(receivedBytes);

        log.info("Received frame from {}: {}", ctx.channel().remoteAddress(), HexUtils.bytesToHex(receivedBytes));

        try {
            // ✅ Pass the frame to DLMS decoder
            decoderService.decode(receivedBytes, ctx);

            // ✅ Handle Load Profile Response if applicable
            if (decoderService.isLoadProfileResponse(receivedBytes)) {
                decoderService.handleLoadProfileResponse(receivedBytes);
            }
        } catch (Exception e) {
            log.error("Error decoding received frame", e);
        }
    }

    private void sendFrames(ChannelHandlerContext ctx, byte[][] frames) {
        for (byte[] frame : frames) {
            try {
                log.info("Sending frame: {}", HexUtils.bytesToHex(frame));
                ByteBuf buffer = Unpooled.wrappedBuffer(frame);
                ctx.writeAndFlush(buffer);
            } catch (Exception e) {
                log.error("Failed to send frame", e);
            }
        }
    }

    private void sendSingleFrame(ChannelHandlerContext ctx, byte[] frame) {
        try {
            log.info("Sending frame: {}", HexUtils.bytesToHex(frame));
            ByteBuf buffer = Unpooled.wrappedBuffer(frame);
            ctx.writeAndFlush(buffer);
        } catch (Exception e) {
            log.error("Failed to send frame", e);
        }
    }

    private void sendLoadProfileRequest(ChannelHandlerContext ctx) {
        try {
            if (client == null) {
                log.error("DLMS Client is not initialized");
                return;
            }

//            // ✅ Build Load Profile Request
//            byte[] loadProfileRequest = requestBuilder.buildLoadProfileRequest();

            // ✅ Identify Load Profile object by name and type
            Object loadProfileName = "1.0.99.1.0.255"; // Example logical name
            ObjectType loadProfileType = ObjectType.PROFILE_GENERIC; // Example object type

            // ✅ Read Load Profile data (e.g., attribute 2)
            byte[][] loadProfileData = client.read(loadProfileName, loadProfileType, 2);

            log.info("Sending Load Profile Request: {}", HexUtils.bytesToHex2(loadProfileData));
            sendFrames(ctx, loadProfileData);
        } catch (Exception e) {
            log.error("Failed to send Load Profile Request", e);
            ctx.close(); // Optionally close connection if request fails
        }
    }

    private void sendTotalImportActiveEnergyRequest(ChannelHandlerContext ctx) {
        try {
            if (client == null) {
                log.error("DLMS Client is not initialized");
                return;
            }

            // ✅ Total Import Active Energy OBIS code (Example: 1-0:1.8.0.255)
            String totalImportEnergyObis = "1.0.1.8.0.255";
            ObjectType energyType = ObjectType.REGISTER; // Correct ObjectType for energy reading

            // ✅ Read Total Import Active Energy data (attribute 2 = current value)
            byte[][] energyData = client.read(totalImportEnergyObis, energyType, 2);

            if (energyData != null) {
                for (byte[] frame : energyData) {
                    log.info("Sending Total Import Active Energy Request: {}", HexUtils.bytesToHex(frame));
                    sendSingleFrame(ctx, frame);
                }
            } else {
                log.warn("No energy data received from the meter.");
            }
        } catch (Exception e) {
            log.error("Failed to send Total Import Active Energy Request: {}", e.getMessage(), e);
            ctx.close(); // Optionally close connection if request fails
        }
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