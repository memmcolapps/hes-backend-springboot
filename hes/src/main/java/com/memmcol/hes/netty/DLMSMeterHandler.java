package com.memmcol.hes.netty;

import com.memmcol.hes.model.ProfileRowDTO;
import com.memmcol.hes.service.*;
import gurux.dlms.internal.GXCommon;
import io.netty.channel.*;
import io.netty.handler.timeout.ReadTimeoutException;
import lombok.extern.slf4j.Slf4j;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class DLMSMeterHandler extends SimpleChannelInboundHandler<byte[]> {
    private final MeterStatusService meterStatusService;
    private final DlmsService dlmsService;

    public DLMSMeterHandler(MeterStatusService meterStatusService, DlmsService dlmsService) {
        this.meterStatusService = meterStatusService;
        this.dlmsService = dlmsService;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("New connection established: {}", ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        meterStatusService.broadcastMeterOffline(MeterConnections.getSerial(ctx.channel()));
        MeterConnections.remove(ctx.channel());
        log.info("🛑 Disconnected channel {}", ctx.channel().remoteAddress());
        ctx.close();
    }
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) throws Exception {
//        log.info("RX (Message received): {}", formatHex(msg));

        if (isLoginMessage(msg)) {
            log.info("RX (Message received): {}", formatHex(msg));
            handleLoginRequest(ctx, msg);

            //For develop test
            try {
                String serial = MeterConnections.getSerial(ctx.channel());
                List<ProfileRowDTO> data = dlmsService.readProfileBuffer(serial, "1.0.99.2.0.255");
            } catch (Exception e){
                log.warn("Failed to read profile buffer {}", e.getMessage());
            }

        } else if (isHeartMessage(msg)) {
            log.info("RX (Message received): {}", formatHex(msg));
            handleHeartRequest(ctx, msg);
        } else if (!isLoginMessage(msg) && !isHeartMessage(msg)){
            // Assume it's a response to a previously sent command
            // Use channel to find the meter serial
            String serial = MeterConnections.getSerial(ctx.channel());
            log.info("RX: {} : {}", serial, GXCommon.toHex(msg));
            if (serial == null) {
                log.warn("❌ Received DLMS response from unknown channel {}", ctx.channel().remoteAddress());
                return;
            }

            String key = RequestResponseService.getLastRequestKey(serial);
            if (key != null) {
                DLMSRequestTracker.complete(key, msg);
            } else {
                log.warn("Received untracked DLMS response from serial: {}", serial);
            }
        } else {
            log.info("RX (Message received): {}", formatHex(msg));
            log.warn("Unknown or unsupported message type.");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
//        log.error("Error in channel", cause);
        if (cause instanceof SocketException) {
            log.warn("⚠️ Socket connection reset by peer: {}", cause.getMessage());
        } else if (cause instanceof ArrayIndexOutOfBoundsException) {
            log.warn("Array Index Out Of Bounds Exception: {}", cause.getMessage());
        } else if (cause instanceof ReadTimeoutException) {
            log.warn("Read Timeout Error: {}", cause.getMessage());
        }else {
            log.error("Unhandled error: {}", cause.getMessage(), cause);
        }
        ctx.close();
    }

    private boolean isLoginMessage(byte[] msg) {
        return msg.length >= 24 && msg[8] == 0x0A && msg[9] == 0x02;
    }
    private void handleLoginRequest(ChannelHandlerContext ctx, byte[] msg) {
        int calcCRCResponse;
        byte[] meterIdBytes = Arrays.copyOfRange(msg, 11, 23); // 12 bytes
        String meterId = new String(meterIdBytes, StandardCharsets.US_ASCII);

        //add meter to connection pool
        Channel channel = ctx.channel();
        MeterConnections.bind(channel, meterId);
        meterStatusService.broadcastMeterOnline(meterId);  //Broadcast online

        byte[] response = new byte[26];
        System.arraycopy(msg, 0, response, 0, 8); // Copy header
        response[8] = (byte) 0xAA;
        response[9] = 0x03;
        response[10] = 0x0C; // Length of meterId
        System.arraycopy(meterIdBytes, 0, response, 11, 12);
        response[23] = 0x00;

        calcCRCResponse = CRC16Utility.countFCS16(response, 9, 15);
        response[24] = (byte) ((calcCRCResponse >> 8) & 0xFF);
        response[25] = (byte) (calcCRCResponse & 0xFF);

        log.debug("About to send login response ");

        // Log using your specified format
        log.info("TX (Response to meter): {}", formatHex(response));
        log.info("Meter No: {}", meterId);
        log.info("Message type: LOGIN");

        ctx.writeAndFlush(response)
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        log.debug("🟢 Write OK to {}", ctx.channel().remoteAddress());
                    } else {
                        log.warn("🔴 Write failed: {}", future.cause().getMessage(), future.cause());
                    }
                });

        log.debug("Login response sent!");
    }

    private boolean isHeartMessage(byte[] msg) {
        return msg.length >= 24 && msg[8] == 0x0C && msg[9] == 0x02;
    }

    private void handleHeartRequest(ChannelHandlerContext ctx, byte[] msg) {
        int calcCRCResponse;
        byte[] meterIdBytes = Arrays.copyOfRange(msg, 11, 23); // 12 bytes
        String meterId = new String(meterIdBytes, StandardCharsets.US_ASCII);


        //add meter to connection pool
        //add meter to connection pool
        Channel channel = ctx.channel();
        MeterConnections.bind(channel, meterId);
        meterStatusService.broadcastMeterOnline(meterId);  //Broadcast online

        byte[] response = new byte[25];
        System.arraycopy(msg, 0, response, 0, 8); // Copy header
        response[8] = (byte) 0xCC;
        response[9] = 0x03;
        response[10] = 0x0C; // Length of meterId
        System.arraycopy(meterIdBytes, 0, response, 11, 12);

        calcCRCResponse = CRC16Utility.countFCS16(response, 9, 14);
        response[23] = (byte) ((calcCRCResponse >> 8) & 0xFF);
        response[24] = (byte) (calcCRCResponse & 0xFF);

        // Log using your specified format
        log.info("TX (Response to meter): {}", formatHex(response));
        log.info("Meter No: {}", meterId);
        log.info("Message type: HEART");

        ctx.writeAndFlush(response);
    }

    private String formatHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}
