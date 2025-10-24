package com.memmcol.hes.netty;

import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.nettyUtils.DlmsRequestContext;
import com.memmcol.hes.nettyUtils.MeterHeartbeatManager;
import com.memmcol.hes.service.*;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.objects.GXDLMSAssociationLogicalName;
import io.netty.channel.*;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.memmcol.hes.nettyUtils.RequestResponseService.*;

@Slf4j
public class DLMSMeterHandler extends SimpleChannelInboundHandler<byte[]> {
    private final MeterStatusService meterStatusService;
    private final DlmsReaderUtils dlmsReaderUtils;
    private final MeterHeartbeatManager heartbeatManager;
    private final ScheduledExecutorService dlmsScheduledExecutor;

    public DLMSMeterHandler(MeterStatusService meterStatusService, DlmsReaderUtils dlmsReaderUtils,
                            MeterHeartbeatManager heartbeatManager,
                            ScheduledExecutorService dlmsScheduledExecutor) {
        this.meterStatusService = meterStatusService;
        this.dlmsReaderUtils = dlmsReaderUtils;
        this.heartbeatManager = heartbeatManager;
        this.dlmsScheduledExecutor = dlmsScheduledExecutor;
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

        // Store a safe copy of inbound frame for later inspection
        MeterConnections.getInboundQueue(ch).offer(Arrays.copyOf(msg, msg.length));

        // --- Step 1: Quick validation ---
        if (msg.length < 10) {
            log.warn("⚠️ Ignored invalid/too-short frame from {}: {}", ch.remoteAddress(),
                    formatHex(msg));
            return;
        }

        // --- Step 2: Identify meter push types dynamically (LOGIN or HEARTBEAT) ---
        byte type = msg[8];
        byte code = msg[9];

        boolean isLoginOrHeart =
                (type == 0x0A && code == 0x02) || // Login
                        (type == 0x0C && code == 0x02);   // Heartbeat

        if (isLoginOrHeart) {
            handleMeterPushedLoginOrHeartMessage(ctx, msg);
            return;
        }

        // --- Step 3: Handle Association Lost frame (if applicable) ---
//        if (isAssociationLost(msg)) {
//            log.info("RX: {} : Association Lost with meter! - {}", serial, formatHex(msg));
//            return;
//        }

        // --- Step 4: Light RX logging ---
        if (serial != null) {
            log.info("RX: {} : {}", serial, formatHex(msg));
            logRx(serial, msg);
        } else {
            log.warn("❌ Received DLMS response from unknown channel {}", ch.remoteAddress());
            return;
        }

        // --- Step 5: Normal DLMS application response tracking ---
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
            log.warn("⚠️ Expired response for CID={} (Meter={}) — total duration={} ms (overdue by {} ms)",
                    correlationId, context.getMeterId(), duration, overdue);
            return;
        }

        inflightRequests.remove(correlationId);
        log.debug("✅ Accepted DLMS response: CID={}, Meters={}", correlationId, context.getMeterId());

        BlockingQueue<byte[]> queue = TRACKER.get(correlationId);
        if (queue != null) {
            queue.offer(Arrays.copyOf(msg, msg.length));
        } else {
            log.warn("⚠️ No waiting queue for correlationId={} — possible timeout or late RX", correlationId);
        }
    }

//    @Override
//    protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) throws Exception {
//        final Channel ch = ctx.channel();
//        final String serial = MeterConnections.getSerial(ch);
//
//        // Store a copy in inbound queue for late-listen / flush support
//        // (avoid storing the same reference if upstream reuses buffers)
//        MeterConnections.getInboundQueue(ch).offer(Arrays.copyOf(msg, msg.length));
//
////        Queue<byte[]> inbound = MeterConnections.getInboundQueue(ctx.channel());
////        log.info("Inbound queue size={} for serial={}", inbound.size(), serial);
//
//        // Message classification
//        if (isLoginMessage(msg)) {
////            handleLoginRequest(ctx, msg);
//            handleMeterPushedLoginOrHeartMessage(ctx, msg);
//            return;
//        }
//
//        if (isHeartMessage(msg)) {
////            handleHeartRequest(ctx, msg);
//            handleMeterPushedLoginOrHeartMessage(ctx, msg);
//            return;
//        }
//
//        if (isAssociationLost(msg)){
//            log.info("RX: {} : Association Lost with meter! - {}", serial, formatHex(msg));
//            return;
//        }
//
//        // Optional: very light RX logging to avoid log flood
//        if (serial != null) {
//            log.info("RX: {} : {}", serial, formatHex(msg));
//            logRx(serial, msg);
//        }
//
//        // Normal DLMS application response
//        if (serial == null) {
//            log.warn("❌ Received DLMS response from unknown channel {}", ch.remoteAddress());
//            return;
//        }
//
//        /**
//         * Handle Incoming Response
//         * tracking correlationId
//         */
//        String correlationId = (String) ch.attr(AttributeKey.valueOf("CID")).get();
//        DlmsRequestContext context = inflightRequests.get(correlationId);
//        if (context == null) {
//            log.warn("❌ Unknown or stale DLMS response: CID={} — discarding", correlationId);
//            return;
//        }
//
//        if (System.currentTimeMillis() > context.getExpiryTime()) {
//            inflightRequests.remove(correlationId);
//            long overdue = context.getOverdueDelay();
//            long duration = context.getDuration();
//            log.warn("⚠️ Expired response for CID={} (MetersEntity={}) — total duration={} ms (overdue by {} ms)",
//                    correlationId, context.getMeterId(), duration, overdue);
//            return;
//        }
//
//        inflightRequests.remove(correlationId);
//        log.debug("✅ Accepted DLMS response: CID={}, MetersEntity={}", correlationId, context.getMeterId());
//
//        BlockingQueue<byte[]> queue = TRACKER.get(correlationId);
//        if (queue != null) {
//            queue.offer(Arrays.copyOf(msg, msg.length));
//        } else {
//            log.warn("⚠️ No waiting queue for correlationId={} — possible timeout or late RX", correlationId);
//        }
//
//
//        // Complete the active tracker for this serial (new API)
////        log.info("channelRead0: Received msg for serial={} — attempting to complete future", serial);
////        boolean completed = DLMSRequestTracker.completeActiveForSerial(serial, msg);
////        if (!completed) {
////            // Backward-compatible fallback if you're still using old key mapping
////            String key = RequestResponseService.getLastRequestKey(serial);
////
////            if (key != null) {
////                DLMSRequestTracker.complete(key, msg);
////            } else {
////                log.warn("Received untracked DLMS frame from meter={}. No active request.", serial);
////            }
////        }
//
//    }

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
    private void handleLoginRequest_Old(ChannelHandlerContext ctx, byte[] msg) {
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

    private void handleLoginRequest(ChannelHandlerContext ctx, byte[] msg) {
        // Example frames:
        // With reserve: 00 01 00 01 00 01 00 11 0A 02 0B 36 32 31 32 34 30 32 32 34 34 33 00 26 D0
        // Without reserve: 00 01 00 01 00 01 00 11 0A 02 0B 36 32 31 32 34 30 32 32 34 34 33 26 D0

        // Extract length of meter ID
        int fixedHeaderLength = 10; // bytes before meter length
        int meterLength = msg[10] & 0xFF; // e.g., 0x0B = 11 bytes
        int meterStart = 11;
        int meterEnd = meterStart + meterLength;

        byte[] meterIdBytes = Arrays.copyOfRange(msg, meterStart, meterEnd);
        String meterId = new String(meterIdBytes, StandardCharsets.US_ASCII);
        log.info("RX: LOGIN: {} : {}", meterId, formatHex(msg));

        // ✅ Check if reserve byte exists (should be 0x00 right after meter ID)
        boolean hasReserve = (msg.length > meterEnd + 2) && (msg[meterEnd] == 0x00);

        // ✅ Register the meter connection
        Channel channel = ctx.channel();
        MeterConnections.bind(channel, meterId);
        meterStatusService.broadcastMeterOnline(meterId);

        // -----------------------------
        // BUILD RESPONSE DYNAMICALLY
        // -----------------------------
        int baseLength = 11 + meterLength; // header + length + meter ID
        int extraBytes = hasReserve ? 1 : 0; // add reserve if present
        int responseLength = baseLength + extraBytes + 2; // CRC (2 bytes)

        byte[] response = new byte[responseLength];

        // Copy first 8 bytes of header
        System.arraycopy(msg, 0, response, 0, 8);

        // Dynamic response type and command
        response[8] = (byte) ((msg[8] & 0xFF) + 0xA0);  // e.g., 0x0A → 0xAA
        response[9] = (byte) ((msg[9] & 0xFF) + 0x01);  // e.g., 0x02 → 0x03

        // Set meter length
        response[10] = (byte) meterLength;

        // Copy meter ID
        System.arraycopy(meterIdBytes, 0, response, 11, meterLength);

        int currentIndex = 11 + meterLength;

        // Add reserve only if it existed in the original frame
        if (hasReserve) {
            response[currentIndex++] = 0x00;
        }

        // ✅ CRC range — start from command (index 9) to last data byte before CRC
        int crcStart = 9;
        int crcLength = currentIndex - crcStart;
        int calcCRCResponse = CRC16Utility.countFCS16(response, crcStart, crcLength);

        // Append CRC
        response[currentIndex++] = (byte) ((calcCRCResponse >> 8) & 0xFF);
        response[currentIndex] = (byte) (calcCRCResponse & 0xFF);

        log.info("TX: LOGIN: {} : {}", meterId, formatHex(response));

        // Send response
        ctx.writeAndFlush(response).addListener((ChannelFutureListener) future -> {
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

    private void handleMeterPushedLoginOrHeartMessage(ChannelHandlerContext ctx, byte[] msg) {
        // Determine message type from msg[8]
        byte msgType = msg[8];
        String typeLabel = switch (msgType) {
            case 0x0A -> "LOGIN";
            case 0x0C -> "HEARTBEAT";
            default -> "UNKNOWN";
        };

        // Extract meter length dynamically
        int fixedHeaderLength = 10; // before meter length byte
        int meterLength = msg[10] & 0xFF;
        int meterStart = 11;
        int meterEnd = meterStart + meterLength;

        byte[] meterIdBytes = Arrays.copyOfRange(msg, meterStart, meterEnd);
        String meterId = new String(meterIdBytes, StandardCharsets.US_ASCII);
        log.info("RX: {}: {} : {}", typeLabel, meterId, formatHex(msg));

        // Check for optional reserve byte
        boolean hasReserve = (msg.length > meterEnd + 2) && (msg[meterEnd] == 0x00);

        // Bind or update meter connection (for LOGIN and HEARTBEAT)
        if (msgType == 0x0A || msgType == 0x0C) {
            MeterConnections.bind(ctx.channel(), meterId);
            meterStatusService.broadcastMeterOnline(meterId);
        }

        // -----------------------------
        // BUILD RESPONSE
        // -----------------------------
        int baseLength = 11 + meterLength;
        int extraBytes = hasReserve ? 1 : 0;
        int responseLength = baseLength + extraBytes + 2; // CRC bytes

        byte[] response = new byte[responseLength];

        // Copy initial 8 header bytes
        System.arraycopy(msg, 0, response, 0, 8);

        // Set dynamic response type & command
        if (msgType == 0x0A) {
            // LOGIN frame → 0xAA response
            response[8] = (byte) ((msg[8] & 0xFF) + 0xA0);
        } else if (msgType == 0x0C) {
            // HEARTBEAT frame → 0xCC response
            response[8] = (byte) ((msg[8] & 0xFF) + 0xC0);
        } else {
            // fallback
            response[8] = (byte) ((msg[8] & 0xFF) + 0xA0);
        }

        // Increment command code (0x02 → 0x03)
        response[9] = (byte) ((msg[9] & 0xFF) + 0x01);

        // Set meter length and copy ID
        response[10] = (byte) meterLength;
        System.arraycopy(meterIdBytes, 0, response, 11, meterLength);

        int currentIndex = 11 + meterLength;

        if (hasReserve) {
            response[currentIndex++] = 0x00;
        }

        // ✅ CRC from index 9 (command) to before CRC
        int crcStart = 9;
        int crcLength = currentIndex - crcStart;
        int calcCRCResponse = CRC16Utility.countFCS16(response, crcStart, crcLength);

        // Append CRC
        response[currentIndex++] = (byte) ((calcCRCResponse >> 8) & 0xFF);
        response[currentIndex] = (byte) (calcCRCResponse & 0xFF);

        log.info("TX: {}: {} : {}", typeLabel, meterId, formatHex(response));

        ctx.writeAndFlush(response).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                log.debug("🟢 {} response sent OK to {}", typeLabel, ctx.channel().remoteAddress());
            } else {
                log.warn("🔴 {} response failed: {}", typeLabel, future.cause().getMessage(), future.cause());
            }
        });

        //Reading Association status (to maintain DLMS association) and Save to DB
        //Only for HEARTBEAT FRAME
        if (msgType == 0x0C) {
            heartbeatManager.handleHeartbeat(meterId);

            dlmsScheduledExecutor.schedule(() -> {
                try {
                    // Read association object status (0.0.40.0.0.255, index 8)
//                    Object result = dlmsReaderUtils.readClock( meterId);  //readClock
                    Object result = dlmsReaderUtils.checkAssociationStatus( meterId);
                    log.debug("Association refreshed for {}, Status: {}", meterId, result);
                } catch (Exception e) {
                    log.error("Failed to refresh association", e);
                }
            }, 5, TimeUnit.SECONDS);
        }
    }

    private String formatHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }


    public boolean isAssociationLost(byte[] response) {
        // Match DLMS "Association Lost" signature
        if (response == null || response.length < 3) return false;
        // Check specific sequence or code e.g., ends with D8 01 01
        int len = response.length;
        return response[len - 3] == (byte) 0xD8
                && response[len - 2] == 0x01
                && response[len - 1] == 0x01;
    }


}
