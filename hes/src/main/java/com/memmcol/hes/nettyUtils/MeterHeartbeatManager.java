package com.memmcol.hes.nettyUtils;

import com.memmcol.hes.gridflex.services.MetersConnectionEventService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
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
    private static final int OFFLINE_THRESHOLD_MINUTES = 10;

    // Thread-safe buffer for lastSeen timestamps
    private final ConcurrentHashMap<String, LocalDateTime> lastSeenBuffer = new ConcurrentHashMap<>();

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

        // Schedule offline detection check
        scheduler.scheduleAtFixedRate(this::checkOfflineMeters,
                10,
                10,
                TimeUnit.MINUTES);
    }

    /** Called whenever a heartbeat is received */
    public void handleHeartbeat(String meterId) {
        lastSeenBuffer.put(meterId, LocalDateTime.now());

        // Optional fallback: flush early if buffer too large
        if (lastSeenBuffer.size() >= BUFFER_FLUSH_THRESHOLD) {
            log.info("⚡ Buffer threshold reached ({} entries). Triggering early flush...", lastSeenBuffer.size());
            flushToDatabase();
        }
    }

    /** Called whenever Netty receives a disconnect event */
    public void handleDisconnect(String meterId) {
        log.warn("⚠️ Disconnect detected for meter [{}]. Updating status to OFFLINE...", meterId);

        // Immediately remove from buffer to prevent stale data
        lastSeenBuffer.remove(meterId);

        // Update database immediately
        try {
            connectionEventService.updateConnectionStatus(meterId, "OFFLINE", LocalDateTime.now());
            log.info("🟡 Meter [{}] marked as OFFLINE and removed from buffer.", meterId);
        } catch (Exception e) {
            log.error("❌ Failed to update DB for meter [{}] disconnect: {}", meterId, e.getMessage(), e);
        }
    }

    /** Periodically flush batched updates to DB */
    private synchronized void flushToDatabase() {
        if (lastSeenBuffer.isEmpty()) return;

        Map<String, LocalDateTime> snapshot = Map.copyOf(lastSeenBuffer);
        lastSeenBuffer.clear();

        CompletableFuture.runAsync(() -> {
            try {
                int successCount = 0;
                for (Map.Entry<String, LocalDateTime> entry : snapshot.entrySet()) {
                    String meterNo = entry.getKey();
                    LocalDateTime lastSeen = entry.getValue();

                    try {
                        // Mark meter as online and record latest timestamp
//                        connectionEventService.updateOrInsertEvent(meterNo, "ONLINE");
                        connectionEventService.updateConnectionStatus(meterNo, "ONLINE", lastSeen);

                        successCount++;
                    } catch (Exception ex) {
                        log.warn("⚠️ Failed to update meter {}: {}", meterNo, ex.getMessage());
                        // Reinsert failed record into buffer for retry
                        lastSeenBuffer.put(meterNo, lastSeen);
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

//    private void checkOfflineMeters() {
//        LocalDateTime now = LocalDateTime.now();
//        lastSeenBuffer.forEach((meterNo, lastSeen) -> {
//            if (Duration.between(lastSeen, now).toMinutes() > OFFLINE_THRESHOLD_MINUTES) {
//                connectionEventService.updateOrInsertEvent(meterNo, "OFFLINE");
//            }
//        });
//    }

    /** Periodically checks for offline meters */
    private void checkOfflineMeters() {
        LocalDateTime now = LocalDateTime.now();
        log.info("🕒 Running offline meter detection...");

        CompletableFuture.runAsync(() -> {
            int offlineCount = 0;

            for (Map.Entry<String, LocalDateTime> entry : lastSeenBuffer.entrySet()) {
                String meterNo = entry.getKey();
                LocalDateTime lastSeen = entry.getValue();

                if (Duration.between(lastSeen, now).toMinutes() > OFFLINE_THRESHOLD_MINUTES) {
                    try {
//                        connectionEventService.updateOrInsertEvent(meterNo, "OFFLINE");
                        connectionEventService.updateConnectionStatus(meterNo, "OFFLINE", lastSeen);
                        offlineCount++;
                        log.info("🚨 Meter {} marked OFFLINE (Last seen: {})", meterNo, lastSeen);
                    } catch (Exception ex) {
                        log.warn("⚠️ Failed to update offline meter {}: {}", meterNo, ex.getMessage());
                    }
                }
            }

            if (offlineCount > 0)
                log.info("✅ {} meters marked OFFLINE during this check.", offlineCount);
            else
                log.info("✅ No offline meters detected at this time.");
        }, meterOpsExecutor);
    }


    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
        meterOpsExecutor.shutdown();
    }
}
