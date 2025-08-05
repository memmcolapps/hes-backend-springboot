package com.memmcol.hes.nettyUtils;

import gurux.dlms.internal.GXCommon;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;

/**
 * Tracks outstanding DLMS request/response pairs at the application layer.
 * A request is identified by a generated key (string) and also associated
 * with a meter serial so inbound handlers can complete the right future even
 * if they don't know the key.
 */
@Slf4j
public class DLMSRequestTracker {
    /* key -> response future */
    private static final ConcurrentMap<String, CompletableFuture<byte[]>> pendingRequests = new ConcurrentHashMap<>();
    /* meterSerial -> active key */
    public static final ConcurrentMap<String, String> serialToKey = new ConcurrentHashMap<>();

    /**
     * Register a new DLMS request for the given meter serial.
     * Returns a unique key that caller should hold and use with waitForResponse().
     */
    public static String register(String serial) {
        String key = serial + "-" + System.nanoTime();
        pendingRequests.put(key, new CompletableFuture<>());
        serialToKey.put(serial, key);
        return key;
    }

    //(Required for Listen-First Strategy)
    public static Pending registerWithFuture(String serial) {
        String key = serial + "-" + System.nanoTime();
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        pendingRequests.put(key, future);
        serialToKey.put(serial, key);
        return new Pending(key, future);
    }

    /**
     * Return the currently active request key for a meter, or null.
     */
    public static String getActiveKeyForSerial(String serial) {
        return serialToKey.get(serial);
    }

    /**
     * Complete the currently active request for a meter. Returns true if a future was completed.
     * <p>
     * Typical call site: your Netty inbound DLMS handler after decoding a full APDU/frame.
     */
    public static boolean completeActiveForSerial(String serial, byte[] response) {
        if (serial == null) return false;
        String key = serialToKey.remove(serial);   // remove mapping once consumed

        if (key == null) {
            log.debug("completeActiveForSerial: no active key for meter={}", serial);
            return false;
        }
        CompletableFuture<byte[]> fut = pendingRequests.remove(key);
        if (fut == null) {
            log.debug("completeActiveForSerial: no pending future for key={} meter={}", key, serial);
            return false;
        }

//        log.info("Completing future for serial={} key={} with RX={}", serial, key, GXCommon.toHex(response));
        fut.complete(response);
        return true;
    }

    public static boolean matchesKey(byte[] frame, String expectedKey) {
        // Simple strategy: match by serial's current key.
        // If you can extract invokeID from frame, use that to match.
        return expectedKey != null && pendingRequests.containsKey(expectedKey);
    }

    /**
     * Complete by explicit key (legacy path).
     */
    public static void complete(String key, byte[] response) {
        CompletableFuture<byte[]> future = pendingRequests.remove(key);
        if (future != null) {
            future.complete(response);
        }
    }

    /**
     * Discard the active request for a meter (used before retry / abort).
     * Any waiting thread will receive an exception.
     */
    public static void discardActiveForSerial(String serial, Throwable cause) {
        if (serial == null) return;
        String key = serialToKey.remove(serial);
        if (key == null) return;
        CompletableFuture<byte[]> fut = pendingRequests.remove(key);
        if (fut != null) {
            if (cause == null) {
                cause = new TimeoutException("Discarded due to retry/flush");
            }
            fut.completeExceptionally(cause);
        }
    }

    /**
     * Blocking wait for response by key.
     * NOTE: If timeout occurs, the future is removed and caller must decide retry strategy.
     */
    public static byte[] waitForResponse(String key, long timeoutMs) throws TimeoutException {
        CompletableFuture<byte[]> fut = pendingRequests.get(key);
        if (fut == null) {
            throw new TimeoutException("No pending request for key: " + key);
        }
        try {
            return fut.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            pendingRequests.remove(key);
            throw te;
        } catch (Exception e) {
            pendingRequests.remove(key);
            throw new TimeoutException("DLMS response failure for key: " + key);
        }
    }

    /* ---------- Optional convenience: non-blocking poll ---------- */

    /**
     * Non-blocking check: return response if already complete, else null.
     * Does not remove the future; caller should still call wait or discard.
     */
    public static byte[] pollNow(String key) {
        CompletableFuture<byte[]> fut = pendingRequests.get(key);
        return fut != null ? fut.getNow(null) : null;
    }

    public static void discard(String key) {
        pendingRequests.remove(key);
    }

    // (Required for Listen-First Strategy)
    public static class Pending {
        private final String key;
        private final CompletableFuture<byte[]> future;

        public Pending(String key, CompletableFuture<byte[]> future) {
            this.key = key;
            this.future = future;
        }

        public String key() {
            return key;
        }

        public CompletableFuture<byte[]> future() {
            return future;
        }
    }


}
