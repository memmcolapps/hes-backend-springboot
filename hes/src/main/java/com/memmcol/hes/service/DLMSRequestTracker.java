package com.memmcol.hes.service;

import java.util.concurrent.*;

public class DLMSRequestTracker {

    private static final ConcurrentMap<String, CompletableFuture<byte[]>> pendingRequests = new ConcurrentHashMap<>();

    public static String register(String serial) {
        String key = serial + "-" + System.nanoTime();
        pendingRequests.put(key, new CompletableFuture<>());
        return key;
    }

    public static void complete(String key, byte[] response) {
        CompletableFuture<byte[]> future = pendingRequests.remove(key);
        if (future != null) {
            future.complete(response);
        }
    }

    public static byte[] waitForResponse(String key, long timeoutMs) throws TimeoutException {
        try {
            return pendingRequests.get(key).get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            pendingRequests.remove(key);
            throw new TimeoutException("DLMS response timeout for key: " + key);
        }
    }
}
