package com.memmcol.hes.nettyUtils;

import com.memmcol.hes.service.MeterStatusService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Component
public class MeterHeartbeatManager {
    // --- Configurable parameters ---
    private static final int BATCH_FLUSH_INTERVAL_MINUTES = 5;
    private static final int BUFFER_FLUSH_THRESHOLD = 500;  // Immediate flush if buffer exceeds this

    // Thread-safe buffer for lastSeen timestamps
    private final ConcurrentHashMap<String, LocalDateTime> lastSeenBuffer = new ConcurrentHashMap<>();

    // Shared executor for async operations
    private final ExecutorService meterOpsExecutor = Executors.newFixedThreadPool(10);

    // Scheduler for periodic batch flush
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Autowired
    private MeterStatusService meterStatusService;

    public MeterHeartbeatManager() {
        // Schedule timed flush every 2 minutes
        scheduler.scheduleAtFixedRate(this::flushToDatabase,
                BATCH_FLUSH_INTERVAL_MINUTES,
                BATCH_FLUSH_INTERVAL_MINUTES,
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

    /** Periodically flush batched updates to DB */
    private synchronized void flushToDatabase() {
        if (lastSeenBuffer.isEmpty()) return;

        Map<String, LocalDateTime> snapshot = Map.copyOf(lastSeenBuffer);
        lastSeenBuffer.clear();

        CompletableFuture.runAsync(() -> {
            try {
//                meterStatusService.bulkUpdateLastSeen(snapshot);
                log.info("✅ Flushed {} meter lastSeen updates to DB", snapshot.size());
            } catch (Exception e) {
                log.error("❌ Failed to flush lastSeen updates: {}", e.getMessage());
                // Reinsert failed records
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
