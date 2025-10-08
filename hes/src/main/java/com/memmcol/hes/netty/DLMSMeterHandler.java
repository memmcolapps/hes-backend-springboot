package com.memmcol.hes.netty;

import com.memmcol.hes.nettyUtils.DlmsRequestContext;
import com.memmcol.hes.service.*;
import io.netty.channel.*;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;

import static com.memmcol.hes.nettyUtils.RequestResponseService.*;

@Slf4j
public class DLMSMeterHandler extends SimpleChannelInboundHandler<byte[]> {
    private final MeterStatusService meterStatusService;

    public DLMSMeterHandler(MeterStatusService meterStatusService, DlmsService dlmsService) {
        this.meterStatusService = meterStatusService;
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

    /**
     * Called with fully-decoded DLMS bytes (thanks to SimpleChannelInboundHandler<byte[]>).
     * We:
     *  1. Copy into per-channel inbound queue (for late-response / flush diagnostics).
     *  2. Classify (login / heartbeat / normal DLMS).
     *  3. Complete the active request tracker for this meter when appropriate.
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) throws Exception {
        final Channel ch = ctx.channel();
        final String serial = MeterConnections.getSerial(ch);

        // Store a copy in inbound queue for late-listen / flush support
        // (avoid storing the same reference if upstream reuses buffers)
        MeterConnections.getInboundQueue(ch).offer(Arrays.copyOf(msg, msg.length));

//        Queue<byte[]> inbound = MeterConnections.getInboundQueue(ctx.channel());
//        log.info("Inbound queue size={} for serial={}", inbound.size(), serial);

        // Message classification
        if (isLoginMessage(msg)) {
            handleLoginRequest(ctx, msg);
            return;
        }

        if (isHeartMessage(msg)) {
            handleHeartRequest(ctx, msg);
            return;
        }

        // Optional: very light RX logging to avoid log flood
        if (serial != null) {
            log.info("RX: {} : {}", serial, formatHex(msg));
            logRx(serial, msg);
        }

        // Normal DLMS application response
        if (serial == null) {
            log.warn("❌ Received DLMS response from unknown channel {}", ch.remoteAddress());
            return;
        }

        /**
         * Handle Incoming Response
         * tracking correlationId
         */
        String correlationId = (String) ch.attr(AttributeKey.valueOf("CID")).get();
        DlmsRequestContext context = inflightRequests.get(correlationId);
        if (context == null) {
            log.warn("❌ Unknown or stale DLMS response: CID={} — discarding", correlationId);
            return;
        }

        if (System.currentTimeMillis() > context.getExpiryTime()) {
            inflightRequests.remove(correlationId);
            long overdue = context.getOverdueDelay();
            long duration = context.getDuration();
            log.warn("⚠️ Expired response for CID={} (MetersEntity={}) — total duration={} ms (overdue by {} ms)",
                    correlationId, context.getMeterId(), duration, overdue);
            return;
        }

        inflightRequests.remove(correlationId);
        log.debug("✅ Accepted DLMS response: CID={}, MetersEntity={}", correlationId, context.getMeterId());

        BlockingQueue<byte[]> queue = TRACKER.get(correlationId);
        if (queue != null) {
            queue.offer(Arrays.copyOf(msg, msg.length));
        } else {
            log.warn("⚠️ No waiting queue for correlationId={} — possible timeout or late RX", correlationId);
        }


        // Complete the active tracker for this serial (new API)
//        log.info("channelRead0: Received msg for serial={} — attempting to complete future", serial);
//        boolean completed = DLMSRequestTracker.completeActiveForSerial(serial, msg);
//        if (!completed) {
//            // Backward-compatible fallback if you're still using old key mapping
//            String key = RequestResponseService.getLastRequestKey(serial);
//
//            if (key != null) {
//                DLMSRequestTracker.complete(key, msg);
//            } else {
//                log.warn("Received untracked DLMS frame from meter={}. No active request.", serial);
//            }
//        }

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
        log.info("RX: LOGIN: {} : {}", meterId, formatHex(msg));

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

        log.info("TX: LOGIN: {} : {}", meterId, formatHex(response));

        ctx.writeAndFlush(response)
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        log.debug("🟢 Write OK to {}", ctx.channel().remoteAddress());
                    } else {
                        log.warn("🔴 Write failed: {}", future.cause().getMessage(), future.cause());
                    }
                });
    }

    private boolean isHeartMessage(byte[] msg) {
        return msg.length >= 24 && msg[8] == 0x0C && msg[9] == 0x02;
    }

    private void handleHeartRequest(ChannelHandlerContext ctx, byte[] msg) {
        int calcCRCResponse;
        byte[] meterIdBytes = Arrays.copyOfRange(msg, 11, 23); // 12 bytes
        String meterId = new String(meterIdBytes, StandardCharsets.US_ASCII);
        log.info("RX: HEART {} : {}", meterId, formatHex(msg));

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

        ctx.writeAndFlush(response);
        log.info("TX: HEART {} : {}", meterId, formatHex(response));
    }

    private String formatHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}
