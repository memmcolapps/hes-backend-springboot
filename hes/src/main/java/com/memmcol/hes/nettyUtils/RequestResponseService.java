package com.memmcol.hes.nettyUtils;

import com.memmcol.hes.application.port.out.TxRxService;
import com.memmcol.hes.netty.NettyBufferUtils;
import com.memmcol.hes.service.MeterConnections;
import gurux.dlms.internal.GXCommon;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.*;

import static com.memmcol.hes.nettyUtils.DLMSRequestTracker.serialToKey;

@Slf4j
@Service
public final class RequestResponseService implements TxRxService {

    private static final Logger DLMS_LOG = LoggerFactory.getLogger("DLMS-TXRX");

    private static final Map<String, BlockingQueue<byte[]>> meterResponseQueues = new ConcurrentHashMap<>();
    private static final Map<String, String> commandKeyMap = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, DlmsRequestContext> inflightRequests = new ConcurrentHashMap<>();
    public static final ConcurrentMap<String, BlockingQueue<byte[]>> TRACKER = new ConcurrentHashMap<>();

//    public String sendRequest(String meterId, Channel channel, byte[] requestPayload) {
//        String correlationId = meterId + "-" + System.nanoTime();
//        long expiryTime = System.currentTimeMillis() + 20000; // 20s wait
//
//        DlmsRequestContext context = new DlmsRequestContext(meterId, correlationId, expiryTime, channel);
//        inflightRequests.put(correlationId, context);
//
//        // Attach correlationId to channel for response matching
//        channel.attr(AttributeKey.valueOf("CID")).set(correlationId);
//
//        // Send request
//        channel.writeAndFlush(requestPayload);
//        log.info("🚀 Sent request to meter={}, CID={}", meterId, correlationId);
//
//        return correlationId;
//    }


    public byte[] sendReceiveWithContext1(
            String meterId,
            byte[] requestData,
            long timeoutMs
    ) throws InterruptedException {
        Channel channel = MeterConnections.getChannel(meterId);
        if (channel != null && channel.isActive()) {
            DlmsRequestContext context = new DlmsRequestContext(meterId, channel, timeoutMs);

            String correlationId = context.getCorrelationId();
            TRACKER.put(correlationId, new LinkedBlockingQueue<>(1)); // track expected RX

            inflightRequests.put(correlationId, context);
            // Add correlationId to MDC for traceability
//        MDC.put("CID", correlationId);

            try {
                log.info("Sending DLMS request to {}, corrId={}", context.getMeterId(), correlationId);
                log.info("TX: {} : {}", context.getMeterId(), GXCommon.toHex(requestData));
                logTx(context.getMeterId(), requestData);
                // Attach correlationId to channel for response matching
                channel.attr(AttributeKey.valueOf("CID")).set(correlationId);
                channel.writeAndFlush(requestData);

                // Await response
                BlockingQueue<byte[]> responseQueue = TRACKER.get(correlationId);
                byte[] response = responseQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);

                if (response == null) {
                    context.markTimeout();
                    log.warn("DLMS timeout for meter={}, correlationId={}, duration={}ms",
                            context.getMeterId(), correlationId, context.getDuration());
                    throw new ReadTimeoutException("DLMS read timeout");
                }

                // Response received on time
                log.info("✅ DLMS success: meter={}, CID={}, duration={}ms",
                        context.getMeterId(), correlationId, context.getDuration());
                return response;

            } finally {
                TRACKER.remove(correlationId); // cleanup to avoid memory leak
//            MDC.remove("CID"); // cleanup MDC
            }
        } else {
            throw new IllegalStateException("Inactive or missing channel for " + meterId);
        }
    }


    @Scheduled(fixedRate = 30000)
    public void cleanExpiredRequests() {
        long now = System.currentTimeMillis();
        inflightRequests.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().getExpiryTime() < now;
            if (expired) {
                log.debug("🧹 Cleaned expired CID={}", entry.getKey());
            }
            return expired;
        });
    }

    public static void registerResponseQueue(String serial) {
        meterResponseQueues.put(serial, new LinkedBlockingQueue<>(1));
    }

    public static void unregisterResponseQueue(String serial) {
        meterResponseQueues.remove(serial);
    }

    public byte[] sendCommand(String serial, byte[] command) {
        log.info("TX: {} : {}", serial, GXCommon.toHex(command));
        Channel channel = MeterConnections.getChannel(serial);
        if (channel != null && channel.isActive()) {
            String reqKey = DLMSRequestTracker.register(serial);
            commandKeyMap.put(serial, reqKey); // Track last request

            channel.writeAndFlush(Unpooled.wrappedBuffer(command));

            try {
                return DLMSRequestTracker.waitForResponse(reqKey, 10000); // 10s timeout
            } catch (TimeoutException e) {  //ReadTimeoutException
                log.warn("DLMS read timeout: {}", e.getMessage());
//                throw new IllegalStateException(e);
                throw new ReadTimeoutException("DLMS read timeout");
            }
        } else {
            throw new IllegalStateException("Inactive or missing channel for " + serial);
        }
    }

    public byte[] sendCommandWithRetry(String serial, byte[] command, int maxRetries, long delayMs) {
        int attempt = 0;
        while (attempt < maxRetries) {
            try {
                return sendCommand(serial, command); // Uses the async tracker from before
            } catch (Exception ex) {
                log.warn("Attempt {} failed for {}: {}", attempt + 1, serial, ex.getMessage());
                attempt++;
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during DLMS retry delay");
                }
            }
        }

        throw new IllegalStateException("DLMS command failed after " + maxRetries + " retries");
    }

    public byte[] sendCommandWithRetry(String serial, byte[] command) {
        Channel channel = MeterConnections.getChannel(serial);
        if (channel == null || !channel.isActive()) {
            throw new IllegalStateException("Inactive or missing channel for " + serial);
        }

        int maxRetries = 3;
        long timeoutMs = 10000;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            String reqKey = DLMSRequestTracker.register(serial);
            try {
                // 1. Send TX
                log.info("TX attempt {} for {}: {}", attempt, serial, GXCommon.toHex(command));
                channel.writeAndFlush(Unpooled.wrappedBuffer(command));

                // 2. Wait for RX
                return DLMSRequestTracker.waitForResponse(reqKey, timeoutMs);

            } catch (TimeoutException e) {
                log.warn("Timeout on attempt {} for meter={} — checking for late RX...", attempt, serial);

                // 3. Check late frames
                byte[] lateFrame = NettyBufferUtils.flushInbound(channel, reqKey);
                if (lateFrame != null) {
                    log.info("Late RX matched for meter={} on attempt {} — returning late frame.", serial, attempt);
                    return lateFrame;
                }

                // 4. If no late frame and not last attempt
                if (attempt == maxRetries) {
                    throw new IllegalStateException("DLMS command failed after " + maxRetries + " retries");
                }
            }
        }
        throw new IllegalStateException("Unexpected failure in sendCommandWithRetry()");
    }

    public byte[] sendCommandWithRetryListenFirst(String serial, byte[] command, long initialTimeoutMs, long lateListenMs, long pollIntervalMs, int maxRetries) throws ReadTimeoutException {

        Channel channel = MeterConnections.getChannel(serial);
        if (channel == null || !channel.isActive()) {
            throw new IllegalStateException("Inactive or missing channel for " + serial);
        }

        Throwable lastError = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {

            // --- Register new request context ---
            DLMSRequestTracker.Pending pending = DLMSRequestTracker.registerWithFuture(serial);
            String reqKey = pending.key();

            // Track active key for inbound correlation (if you use a map)
            commandKeyMap.put(serial, reqKey);

            // --- (Optional) Flush inbound BEFORE the very first send, if you suspect stale noise ---
            if (attempt == 1) {
                NettyBufferUtils.flushInbound(channel);
            }

            // --- Send ---
            log.info("TX attempt {}/{} meter={} : {}", attempt, maxRetries, serial, GXCommon.toHex(command));
            channel.writeAndFlush(Unpooled.wrappedBuffer(command));

            // --- Wait normal window ---
            byte[] resp = null;
            try {
                resp = pending.future().get(initialTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException te) {
                lastError = te;
                log.warn("Initial timeout ({} ms) attempt {}/{} meter={} — entering late listen {} ms", initialTimeoutMs, attempt, maxRetries, serial, lateListenMs);
            } catch (Exception ex) {
                lastError = ex;
                log.warn("Error waiting for DLMS response attempt {}/{} meter={} : {}", attempt, maxRetries, serial, ex.getMessage());
            }

            // --- Normal response arrived ---
            if (resp != null) {
                log.debug("RX meter={} (attempt {}) length={}", serial, attempt, resp.length);
                // Ensure tracker cleanup in case inbound didn't remove
                DLMSRequestTracker.complete(reqKey, resp);
                return resp;
            }

            // --- Late listen window (no resend) ---
            if (resp == null && lateListenMs > 0) {
                byte[] late = waitLateWindow(pending.future(), lateListenMs, pollIntervalMs);
                if (late != null) {
                    log.info("Late RX after timeout meter={} attempt={} returning late frame.", serial, attempt);
                    DLMSRequestTracker.complete(reqKey, late);
                    return late;
                }
            }

            // --- No response even after late window ---
            // Discard this pending & flush inbound before next attempt
            DLMSRequestTracker.discardActiveForSerial(serial, lastError);
            NettyBufferUtils.flushInbound(channel);

            if (attempt == maxRetries) {
                throw new ReadTimeoutException("DLMS read timeout after " + maxRetries + " attempts (meter=" + serial + ")");
            }

            // Backoff between attempts
            sleepQuiet(10); // tune or exponential if desired
        }

        // Should never reach here
        throw new ReadTimeoutException("Unexpected send retry failure meter=" + serial);
    }

    /**
     * 3. Send Once, Listen Up To 20s Implementation
     * Generic Worker (configurable total timeout + flush option)
     *
     * @param serial
     * @param command
     * @param initialTimeoutMs
     * @param lateListenMs
     * @param pollIntervalMs
     * @return
     * @throws ReadTimeoutException
     */
    public byte[] sendOnceListen(String serial, byte[] command, long initialTimeoutMs, long lateListenMs, long pollIntervalMs) throws ReadTimeoutException {

        Channel channel = MeterConnections.getChannel(serial);
        if (channel == null || !channel.isActive()) {
            throw new IllegalStateException("Inactive or missing channel for " + serial);
        }

        // --- Register new request context ---
        DLMSRequestTracker.Pending pending = DLMSRequestTracker.registerWithFuture(serial);
        String reqKey = pending.key();

        // Track active key for inbound correlation
        commandKeyMap.put(serial, reqKey);

        // Flush inbound to remove stale noise
        NettyBufferUtils.flushInbound(channel);

        // --- Send once only ---
        log.info("TX meter={} : {}", serial, GXCommon.toHex(command));
        channel.writeAndFlush(Unpooled.wrappedBuffer(command));

        // --- Initial wait window ---
        try {
            byte[] resp = pending.future().get(initialTimeoutMs, TimeUnit.MILLISECONDS);
            if (resp != null) {
//                log.debug("RX meter={} in initial window: {}", serial, GXCommon.toHex(resp));
                return resp;
            }
        } catch (TimeoutException te) {
            log.warn("Initial timeout ({} ms) meter={} — entering listen mode for {} ms", initialTimeoutMs, serial, lateListenMs);
        } catch (Exception ex) {
            log.warn("DLMS exception waiting for response meter={} : {}", serial, ex.getMessage());
        }

        // --- Late listen block ---
        try {
            byte[] late = waitLateWindow2(pending.future(), lateListenMs, pollIntervalMs);
            if (late != null) {
                log.debug("Late RX meter={} after listen mode: {}", serial, GXCommon.toHex(late));
                return late;
            }
            // --- Still no response ---
            throw new ReadTimeoutException("DLMS read timeout (TX-once only, meter=" + serial + ")");
        } finally {
            // Clean up tracker after final outcome (success or timeout)
            DLMSRequestTracker.discardActiveForSerial(serial, new ReadTimeoutException("No response after full listen window"));
        }
    }

    //Supporting Helper: Late Window Poll for SendOnce
    private byte[] waitLateWindow2(CompletableFuture<byte[]> future, long totalMs, long intervalMs) {

        long waited = 0;
        while (waited < totalMs) {
            try {
                byte[] result = future.get(intervalMs, TimeUnit.MILLISECONDS);
                if (result != null) {
                    log.debug("Received during late window after {} ms", waited);
                    return result;
                }
            } catch (TimeoutException te) {
                waited += intervalMs;
//                log.debug("No data yet in late window. Waited {} ms so far.", waited);
            } catch (Exception e) {
//                log.warn("Error during late window wait: {}", e.getMessage());
                return null;
            }
        }
        return null;
    }


    //Supporting Helper: Late Window Poll
    private byte[] waitLateWindow(CompletableFuture<byte[]> fut, long lateListenMs, long pollIntervalMs) {
        final long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(lateListenMs);
        for (; ; ) {
            byte[] val = fut.getNow(null);
            if (val != null) return val;
            if (System.nanoTime() >= deadlineNanos) return null;
            sleepQuiet(pollIntervalMs);
        }
    }

    //Sleep helper
    private void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }


    public static BlockingQueue<byte[]> getQueue(String serial) {
        return meterResponseQueues.get(serial);
    }

    public static String getLastRequestKey(String serial) {
        return commandKeyMap.get(serial);
    }


    public static void logTx(String meterSerial, String msg) {
        try {
            MDC.put("meter", meterSerial);   // <-- used by logback discriminator
            DLMS_LOG.info("[MSG]:{}", msg);
        } finally {
            MDC.remove("meter");
        }
    }

    public static void logTx(String meterSerial, byte[] frame) {
        try {
            MDC.put("meter", meterSerial);   // <-- used by logback discriminator
            DLMS_LOG.info("[TX]:{}: {}", meterSerial, toHex(frame));
        } finally {
            MDC.remove("meter");
        }
    }

    public static void logRx(String meterSerial, byte[] frame) {
        try {
            MDC.put("meter", meterSerial);
            DLMS_LOG.info("[RX]:{}: {}", meterSerial,toHex(frame));
        } finally {
            MDC.remove("meter");
        }
    }

    private static String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    @Override
    public byte[] sendReceiveWithContext(String meterId, byte[] requestData, long timeoutMs) throws Exception {
        Channel channel = MeterConnections.getChannel(meterId);
        if (channel != null && channel.isActive()) {
            DlmsRequestContext context = new DlmsRequestContext(meterId, channel, timeoutMs);

            String correlationId = context.getCorrelationId();
            TRACKER.put(correlationId, new LinkedBlockingQueue<>(1)); // track expected RX

            inflightRequests.put(correlationId, context);
            // Add correlationId to MDC for traceability
//        MDC.put("CID", correlationId);

            try {
                log.info("Sending DLMS request to {}, corrId={}", context.getMeterId(), correlationId);
                log.info("TX: {} : {}", context.getMeterId(), GXCommon.toHex(requestData));
                logTx(context.getMeterId(), requestData);
                // Attach correlationId to channel for response matching
                channel.attr(AttributeKey.valueOf("CID")).set(correlationId);
                channel.writeAndFlush(requestData);

                // Await response
                BlockingQueue<byte[]> responseQueue = TRACKER.get(correlationId);
                byte[] response = responseQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);

                if (response == null) {
                    context.markTimeout();
                    log.warn("DLMS timeout for meter={}, correlationId={}, duration={}ms",
                            context.getMeterId(), correlationId, context.getDuration());
                    throw new ReadTimeoutException("DLMS read timeout");
                }

                // Response received on time
                log.info("✅ DLMS success: meter={}, CID={}, duration={}ms",
                        context.getMeterId(), correlationId, context.getDuration());
                return response;

            } finally {
                TRACKER.remove(correlationId); // cleanup to avoid memory leak
//            MDC.remove("CID"); // cleanup MDC
            }
        } else {
            throw new IllegalStateException("Inactive or missing channel for " + meterId);
        }
    }
}
