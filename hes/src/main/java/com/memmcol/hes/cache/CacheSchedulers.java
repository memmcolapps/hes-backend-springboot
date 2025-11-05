package com.memmcol.hes.cache;

import com.memmcol.hes.gridflex.services.DashboardAsyncService;
import com.memmcol.hes.gridflex.services.DashboardService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
@EnableScheduling
public class CacheSchedulers {
    private final DashboardAsyncService asyncService;
    private final CacheManager cacheManager;

    // Run every 10 minutes
    @Scheduled(fixedRate = 10 * 60 * 1000)  // 10 min
    public void warmDashboardCache() {
        try {
            log.info("🔥 Warming up dashboard caches (summary, logs, rate, report)…");

            CompletableFuture.allOf(
                    asyncService.getMeterSummaryAsync(),
                    asyncService.getCommunicationLogsAsync(),
                    asyncService.getSchedulerRateAsync(),
                    asyncService.getCommunicationReportAsync()
            ).join();
            log.info("✅ Dashboard caches refreshed successfully.");
        } catch (Exception e) {
            log.error("❌ Error during dashboard cache warmup: {}", e.getMessage());
        }
    }

    @PostConstruct
    public void warmOnStartup() {
        log.info("🚀 Initial dashboard cache warmup at startup...");
        warmDashboardCache();
    }

    /**
     * Clears dashboard-related caches every midnight (00:00)
     */
    @Scheduled(cron = "0 0 0 * * *")  // every day at midnight
    public void evictDashboardCaches() {
        log.info("🕛 Running nightly cache eviction...");
        // List your cache names as registered in your CaffeineCacheManager
        String[] cacheNames = {"dashboardMeterSummary",
                "dashboardCommunicationLogs",
                "dashboardSchedulerRate",
                "dashboardCommunicationReport"};

        for (String name : cacheNames) {
            if (cacheManager.getCache(name) != null) {
                Objects.requireNonNull(cacheManager.getCache(name)).clear();
                log.info("✅ Cleared cache: {}",  name);
            }
        }
    }


}
