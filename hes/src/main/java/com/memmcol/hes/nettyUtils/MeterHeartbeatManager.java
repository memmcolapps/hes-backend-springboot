package com.memmcol.hes.nettyUtils;

import com.memmcol.hes.gridflex.services.MetersConnectionEventService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Component
public class MeterHeartbeatManager {
    /*TODO:
    *  1. Create a new buffer for Offline meters.
    *  2. Remove from lastSeenBuffer and add to Offline meters buffer.
    *  3. use flyway script to rename update_at to offline_time in the meters_connection_event table.
    *  4. But lastSeenBuffer and Offline meters buffer should run under the schedule executor.
    *  5.   */

    // --- Configurable parameters ---
    private static final int BATCH_FLUSH_INTERVAL_MINUTES = 5;
    private static final int BUFFER_FLUSH_THRESHOLD = 500;  // Immediate flush if buffer exceeds this

    // Thread-safe buffer for lastSeen timestamps
    private final ConcurrentHashMap<String, String> lastSeenBuffer = new ConcurrentHashMap<>();

    // Shared executor for async operations
    private final ExecutorService meterOpsExecutor = Executors.newFixedThreadPool(10);

    // Scheduler for periodic batch flush
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Autowired
    private MetersConnectionEventService connectionEventService;

    public MeterHeartbeatManager() {
        // Schedule timed flush every 2 minutes
        scheduler.scheduleAtFixedRate(this::flushToDatabase,
                BATCH_FLUSH_INTERVAL_MINUTES,
                BATCH_FLUSH_INTERVAL_MINUTES,
                TimeUnit.MINUTES);
    }

    /** Called whenever Netty receives a heartbeat, login and disconnect event */
    public void handleStatus(String meterId, String status) {
        lastSeenBuffer.put(meterId, status);

        // Optional fallback: flush early if buffer too large
        if (lastSeenBuffer.size() >= BUFFER_FLUSH_THRESHOLD) {
            log.info("⚡ Buffer threshold reached ({} entries). Triggering early flush...", lastSeenBuffer.size());
            flushToDatabase();
        }
    }

    /** Periodically flush batched updates to DB */
    private synchronized void flushToDatabase() {
        if (lastSeenBuffer.isEmpty()) return;

        Map<String, String> snapshot = Map.copyOf(lastSeenBuffer);
        lastSeenBuffer.clear();

        CompletableFuture.runAsync(() -> {
            try {
                int successCount = 0;
                for (Map.Entry<String, String> entry : snapshot.entrySet()) {
                    String meterNo = entry.getKey();
                    String status = entry.getValue();

                    try {
                        connectionEventService.updateConnectionStatus(meterNo, status, ZonedDateTime.now(ZoneId.systemDefault()).toLocalDateTime());
                        successCount++;
                    } catch (Exception ex) {
                        log.warn("⚠️ Failed to update meter {}: {}", meterNo, ex.getMessage());
                        // Reinsert failed record into buffer for retry
                        lastSeenBuffer.put(meterNo, status);
                    }
                }

                log.info("✅ Flushed {} / {} meter lastSeen updates to DB", successCount, snapshot.size());
            } catch (Exception e) {
                log.error("❌ Failed to flush lastSeen batch: {}", e.getMessage());
                // Restore all in case of fatal failure
                lastSeenBuffer.putAll(snapshot);
            }
        }, meterOpsExecutor);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
        meterOpsExecutor.shutdown();
    }
}
