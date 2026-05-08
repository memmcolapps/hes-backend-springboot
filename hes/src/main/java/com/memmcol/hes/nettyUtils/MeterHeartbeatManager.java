package com.memmcol.hes.nettyUtils;

import com.memmcol.hes.dto.MeterUpdateDTO;
import com.memmcol.hes.repository.MetersConnectionEventBatchRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Component
public class MeterHeartbeatManager {

    // --- Tuned for near real-time ---
    private static final int FLUSH_INTERVAL_SECONDS = 15;
    private static final int OFFLINE_CHECK_INTERVAL_SECONDS = 10;
    private static final int OFFLINE_THRESHOLD_MINUTES = 2;
    private static final int BUFFER_FLUSH_THRESHOLD = 1000;

    // --- State tracking ---
    private final ConcurrentHashMap<String, MeterState> stateMap = new ConcurrentHashMap<>();

    // --- Write buffer (latest per meter) ---
    private final ConcurrentHashMap<String, MeterUpdateDTO> writeBuffer = new ConcurrentHashMap<>();

    // --- Executors ---
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private final ExecutorService dbExecutor = new ThreadPoolExecutor(
            10,
            10,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(2000),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    private final MetersConnectionEventBatchRepository batchRepository;

    public MeterHeartbeatManager(MetersConnectionEventBatchRepository batchRepository) {
        this.batchRepository = batchRepository;

        scheduler.scheduleAtFixedRate(
                this::flushToDatabase,
                FLUSH_INTERVAL_SECONDS,
                FLUSH_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );

        scheduler.scheduleAtFixedRate(
                this::detectOfflineMeters,
                OFFLINE_CHECK_INTERVAL_SECONDS,
                OFFLINE_CHECK_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    /**
     * Called on every heartbeat / login / disconnect
     */
    public void handleStatus(String meterId, String status) {

        // Netty can call disconnect handlers before a meter has bound its serial.
        // ConcurrentHashMap does not allow null keys/values, so guard aggressively here.
        if (meterId == null || meterId.isBlank()) {
            log.warn("⚠️ Ignoring meter status update with null/blank meterId (status={})", status);
            return;
        }
        final String safeStatus = (status == null) ? "UNKNOWN" : status;

        long now = System.currentTimeMillis();
        LocalDateTime time = LocalDateTime.now();

        // 🔥 ALWAYS update buffer (latest wins)
        writeBuffer.put(meterId, new MeterUpdateDTO(meterId, safeStatus, time));

        // Update in-memory state
        stateMap.compute(meterId, (k, state) -> {
            if (state == null) {
                return new MeterState(safeStatus, now);
            }
            state.status = safeStatus;
            state.lastSeenEpoch = now;
            return state;
        });

        // Early flush under pressure
        if (writeBuffer.size() >= BUFFER_FLUSH_THRESHOLD) {
            flushToDatabase();
        }
    }

    /**
     * Detect offline meters based on heartbeat timeout
     */
    private void detectOfflineMeters() {

        long now = System.currentTimeMillis();
        long thresholdMillis = TimeUnit.MINUTES.toMillis(OFFLINE_THRESHOLD_MINUTES);

        for (Map.Entry<String, MeterState> entry : stateMap.entrySet()) {

            String meterId = entry.getKey();
            MeterState state = entry.getValue();

            if ("ONLINE".equals(state.status) &&
                    (now - state.lastSeenEpoch) > thresholdMillis) {

                log.info("🔻 Meter {} marked OFFLINE (timeout)", meterId);

                state.status = "OFFLINE";

                // Buffer OFFLINE update (same pipeline)
                writeBuffer.put(
                        meterId,
                        new MeterUpdateDTO(meterId, "OFFLINE", LocalDateTime.now())
                );
            }
        }
    }

    /**
     * Batch flush to DB (UPSERT latest state)
     */
    private void flushToDatabase() {

        if (writeBuffer.isEmpty()) return;

        Map<String, MeterUpdateDTO> snapshot = new HashMap<>();

        for (Map.Entry<String, MeterUpdateDTO> entry : writeBuffer.entrySet()) {

            if (snapshot.size() >= BUFFER_FLUSH_THRESHOLD) break;

            if (writeBuffer.remove(entry.getKey(), entry.getValue())) {
                snapshot.put(entry.getKey(), entry.getValue());
            }
        }

        if (snapshot.isEmpty()) return;

        try {
            CompletableFuture.runAsync(() -> {
                try {

                    List<MeterUpdateDTO> batch = new ArrayList<>(snapshot.values());

                    batchRepository.batchUpsert(batch);

                    log.info("✅ Flushed {} meter updates", batch.size());

                } catch (Exception e) {

                    log.error("❌ Batch flush failed. Re-queueing {}", snapshot.size(), e);

                    writeBuffer.putAll(snapshot);
                }

            }, dbExecutor);
        } catch (RejectedExecutionException e) {
            // If the DB executor is saturated, never block the scheduler thread (and never drop updates).
            log.warn("⚠️ DB executor saturated; re-queueing {} meter updates", snapshot.size(), e);
            writeBuffer.putAll(snapshot);
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
        dbExecutor.shutdown();
    }

    // --- Internal state model ---
    static class MeterState {
        volatile String status;
        volatile long lastSeenEpoch;

        MeterState(String status, long lastSeenEpoch) {
            this.status = status;
            this.lastSeenEpoch = lastSeenEpoch;
        }
    }
}