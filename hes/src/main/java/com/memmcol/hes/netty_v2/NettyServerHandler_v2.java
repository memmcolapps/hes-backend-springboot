package com.memmcol.hes.netty_v2;

import com.memmcol.hes.service.RequestResponseService;
import com.memmcol.hes.service.DLMSRequestTracker;
import com.memmcol.hes.service.MMXCRC16;
import com.memmcol.hes.service.MeterConnections;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Slf4j
public class NettyServerHandler_v2 extends SimpleChannelInboundHandler<byte[]> {

    private final AttributeKey<byte[]> dataKey = AttributeKey.valueOf("dataBuf");
    private final AttributeKey<Integer> dataLen = AttributeKey.valueOf("dataBufLen");
    private byte[] fullData = null;

    private final RequestResponseService clientService;

    public NettyServerHandler_v2(RequestResponseService clientService) {
        this.clientService = clientService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) {
        //Setup the attribute keys
        byte[] dataBuf = ctx.channel().attr(dataKey).get();
        int dataBufLen = 0;

        boolean allocBuf = dataBuf == null;
        if (allocBuf) {
            dataBuf = new byte[1024];
        }

        boolean allocBufLen = ctx.channel().attr(dataLen).get() == null;
        if (!allocBufLen) {
            dataBufLen = ctx.channel().attr(dataLen).get();
        }

        //Read from buffer
        System.arraycopy(msg, 0, dataBuf, (int) dataBufLen, msg.length);
//        System.out.println("BUGGING-- "+new String(msg));
        dataBufLen += msg.length;
        ctx.channel().attr(dataLen).set(dataBufLen);

        if (allocBuf) {
            ctx.channel().attr(dataKey).set(dataBuf);
        }
        ctx.channel().read();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        byte[] dataBuf = ctx.channel().attr(dataKey).get();
        try {
            if (dataBuf != null) {
                //get the full data from the buffer
                int dataBufLen = ctx.channel().attr(dataLen).get();
                fullData = new byte[(int) dataBufLen];
                System.arraycopy(dataBuf, 0, fullData, 0, (int) dataBufLen);
                //Reset the attr
                ctx.channel().attr(dataKey).set(null);
                ctx.channel().attr(dataLen).set(0);

                log.info("RX (Message received): {}", formatHex(fullData));

                if (isLoginMessage(fullData)) {
                    handleLoginRequest(ctx, fullData);
                } else if (isHeartMessage(fullData)) {
                    handleHeartRequest(ctx, fullData);
                } else if (!isLoginMessage(fullData) && !isHeartMessage(fullData)) {
                    // Assume it's a response to a previously sent command
                    // Use channel to find the meter serial
                    String serial = MeterConnections.getSerial(ctx.channel());
                    if (serial == null) {
                        log.warn("❌ Received DLMS response from unknown channel {}", ctx.channel().remoteAddress());
                        return;
                    }

                    String key = RequestResponseService.getLastRequestKey(serial);
                    if (key != null) {
                        DLMSRequestTracker.complete(key, fullData);
                    } else {
                        log.warn("Received untracked DLMS response from serial: {}", serial);
                    }
                } else {
                    log.warn("Unknown or unsupported message type.");
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }

    }

//    @Override
//    protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) {
//
//        log.info("RX (Message received): {}", formatHex(msg));
//
//        if (isLoginMessage(msg)) {
//            handleLoginRequest(ctx, msg);
//        } else if (isHeartMessage(msg)) {
//            handleHeartRequest(ctx, msg);
//        } else if (!isLoginMessage(msg) && !isHeartMessage(msg)){
//            // Assume it's a response to a previously sent command
//            // Use channel to find the meter serial
//            String serial = MeterConnections.getSerial(ctx.channel());
//            if (serial == null) {
//                log.warn("❌ Received DLMS response from unknown channel {}", ctx.channel().remoteAddress());
//                return;
//            }
//
//            String key = RequestResponseService.getLastRequestKey(serial);
//            if (key != null) {
//                DLMSRequestTracker.complete(key, msg);
//            } else {
//                log.warn("Received untracked DLMS response from serial: {}", serial);
//            }
//        } else {
//            log.warn("Unknown or unsupported message type.");
//        }
//    }
//
//    @Override
//    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
//        ctx.flush();   // Ensure response is flushed if decoder doesn't auto-flush
//    }

    private void handleLoginRequest(ChannelHandlerContext ctx, byte[] msg) {
        int calcCRCResponse;
        MMXCRC16 mmxcrc16 = new MMXCRC16();
        byte[] meterIdBytes = Arrays.copyOfRange(msg, 11, 23); // 12 bytes
        String meterId = new String(meterIdBytes, StandardCharsets.US_ASCII);

        //add meter to connection pool
        Channel channel = ctx.channel();
        MeterConnections.bind(channel, meterId);

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

        log.info("About to send login response from NettyServerHandler_v2");

        // Log using your specified format
        log.info("TX (Response to meter): {}", formatHex(response));
        log.info("Meter No: {}", meterId);
        log.info("Message type: LOGIN");

        // Write response with listener
        ctx.writeAndFlush(response).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                log.info("🟢 Write OK to {}", ctx.channel().remoteAddress());
            } else {
                log.error("🔴 Write failed: {}", future.cause().getMessage(), future.cause());
            }
        });
        log.info("Login response sent!");
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
        MeterConnections.remove(ctx.channel());
        log.info("🛑 Disconnected channel {}", ctx.channel().remoteAddress());
        ctx.close();
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


        //add meter to connection pool
        //add meter to connection pool
        Channel channel = ctx.channel();
        MeterConnections.bind(channel, meterId);

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


}