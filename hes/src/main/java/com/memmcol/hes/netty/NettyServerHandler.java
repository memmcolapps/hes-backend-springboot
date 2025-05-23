package com.memmcol.hes.netty;

import com.memmcol.hes.service.MMXCRC16;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.enums.Authentication;
import gurux.dlms.enums.InterfaceType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Slf4j
public class NettyServerHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private GXDLMSClient client;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buf) {
        byte[] msg = new byte[buf.readableBytes()];
        buf.readBytes(msg);
        buf.resetReaderIndex(); //Reset reader index for subsequent operation

        log.info("RX (Message received): {}", formatHex(msg));

        if (isLoginMessage(msg)) {
            handleLoginRequest(ctx, msg);
        } else if (isHeartMessage(msg)) {
            handleHeartRequest(ctx, msg);
        } else {
            log.warn("Unknown or unsupported message type.");
        }
    }

    private void handleLoginRequest(ChannelHandlerContext ctx, byte[] msg) {
        int calcCRCResponse;
        MMXCRC16 mmxcrc16 = new MMXCRC16();
        byte[] meterIdBytes = Arrays.copyOfRange(msg, 11, 23); // 12 bytes
        String meterId = new String(meterIdBytes, StandardCharsets.US_ASCII);

        byte[] response = new byte[26];
        System.arraycopy(msg, 0, response, 0, 8); // Copy header
        response[8] = (byte) 0xAA;
        response[9] = 0x03;
        response[10] = 0x0C; // Length of meterId
        System.arraycopy(meterIdBytes, 0, response, 11, 12);
        response[23] = 0x00;

        calcCRCResponse = mmxcrc16.countFCS16(response, 9, 14);
        response[24] = (byte) ((calcCRCResponse >> 8) & 0xFF);
        response[25] = (byte) (calcCRCResponse & 0xFF);

        // Log using your specified format
        log.info("TX (Response to meter): {}", formatHex(response));
        log.info("Meter No: {}", meterId);
        log.info("Message type: LOGIN");

        ctx.writeAndFlush(Unpooled.wrappedBuffer(response));

        ReferenceCountUtil.release(Unpooled.wrappedBuffer(response));  //Release to prevent memory leakage

        // Trigger DLMS initialization after login
        initiateDLMSConnection(ctx, meterId);
    }

    private boolean isLoginMessage(byte[] msg) {
        return msg.length >= 24 && msg[8] == 0x0A && msg[9] == 0x02;
    }

    private int calculateSimpleChecksum(byte[] data) {
        int crc = 0;
        for (byte b : data) {
            crc += b & 0xFF;
        }
        return crc & 0xFFFF;
    }

    private String formatHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("New connection established: {}", ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("Connection closed: {}", ctx.channel().remoteAddress());
        if (client != null) {
            log.info("DLMS client disconnected");
            client = null;
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
//        log.error("Error in channel", cause);
        if (cause instanceof SocketException) {
            log.warn("⚠️ Socket connection reset by peer: {}", cause.getMessage());
        } else if (cause instanceof ArrayIndexOutOfBoundsException) {
            log.warn("Array Index Out Of Bounds Exception: {}", cause.getMessage());
    } else {
            log.error("Unhandled error: {}", cause.getMessage(), cause);
        }
        ctx.close();
    }

    private boolean isHeartMessage(byte[] msg) {
        return msg.length >= 24 && msg[8] == 0x0C && msg[9] == 0x02;
    }

    private void handleHeartRequest(ChannelHandlerContext ctx, byte[] msg) {
        int calcCRCResponse;
        MMXCRC16 mmxcrc16 = new MMXCRC16();
        byte[] meterIdBytes = Arrays.copyOfRange(msg, 11, 23); // 12 bytes
        String meterId = new String(meterIdBytes, StandardCharsets.US_ASCII);

        byte[] response = new byte[25];
        System.arraycopy(msg, 0, response, 0, 8); // Copy header
        response[8] = (byte) 0xCC;
        response[9] = 0x03;
        response[10] = 0x0C; // Length of meterId
        System.arraycopy(meterIdBytes, 0, response, 11, 12);

        calcCRCResponse = mmxcrc16.countFCS16(response, 9, 14);
        response[23] = (byte) ((calcCRCResponse >> 8) & 0xFF);
        response[24] = (byte) (calcCRCResponse & 0xFF);

        // Log using your specified format
        log.info("TX (Response to meter): {}", formatHex(response));
        log.info("Meter No: {}", meterId);
        log.info("Message type: HEART");

        ctx.writeAndFlush(Unpooled.wrappedBuffer(response));

        ReferenceCountUtil.release(Unpooled.wrappedBuffer(response));  //Release to prevent memory leakage
    }

    private void initiateDLMSConnection(ChannelHandlerContext ctx, String meterId) {
        try {
            if (client == null) {
                client = new GXDLMSClient(
                        true,                        // Use Logical Name referencing
                        16,                          // Client address (typically 16 for public client)
                        1,                           // Server address (logical/short meter address)
                        Authentication.LOW,          // Choose NONE, LOW, or HIGH based on your meter config
                        "00000000",                  // Password for LOW authentication
                        InterfaceType.WRAPPER        // WRAPPER for TCP/IP; HDLC for serial
                );
            }

            log.info("🔌 Initiating DLMS handshake for Meter: {}", meterId);

            // Step 1: Generate SNRM frame (used for HDLC; WRAPPER may not need it, but we'll include for safety)
            byte[] snrm = client.snrmRequest();
            log.info("TX (SNRM request): {}", formatHex(snrm));
            ctx.writeAndFlush(Unpooled.wrappedBuffer(snrm));

            // Step 2: Generate AARQ frame (Association Request)
            byte[][] aarq = client.aarqRequest();
            for (byte[] frame : aarq) {
                log.info("TX (AARQ frame): {}", formatHex(frame));
                ctx.writeAndFlush(Unpooled.wrappedBuffer(frame));
            }

            log.info("✅ DLMS initialization (SNRM + AARQ) sent to meter {}", meterId);

        } catch (Exception e) {
            log.error("❌ Failed to initiate DLMS connection for Meter {}: {}", meterId, e.getMessage(), e);
        }
    }


}