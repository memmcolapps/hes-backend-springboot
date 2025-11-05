package com.memmcol.hes.cache;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CaffeineCacheConfig {
//    ✅ 2. Create CacheConfig with per-cache TTL and max size
    @Bean
    public CacheManager cacheManager() {
        // Define cache specs per cache name
        Map<String, Caffeine<Object, Object>> specs = new HashMap<>();

        specs.put("obisMappings", Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .recordStats());

        specs.put("recentConfigs", Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .recordStats());

        specs.put("localMetadata", Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterAccess(20, TimeUnit.MINUTES)
                .recordStats());

        specs.put("profileMetadata", Caffeine.newBuilder()  // ✅ NEW
                .maximumSize(500)
                .expireAfterWrite(12, TimeUnit.HOURS)
                .recordStats());

        specs.put("lastProfileTimestamp", Caffeine.newBuilder()  // ✅ NEW
                .maximumSize(500)
                .expireAfterWrite(12, TimeUnit.HOURS)
                .recordStats());

        specs.put("profileCapturePeriod", Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(12, TimeUnit.HOURS)
                .recordStats());

        specs.put("modelScalerMap", Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(12, TimeUnit.HOURS)
                .recordStats());

        specs.put("meterRatios", Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(12, TimeUnit.HOURS)
                .recordStats());

        specs.put("modelDescriptionMap", Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(12, TimeUnit.HOURS)
                .recordStats());

        specs.put("eventCodeLookup", Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(24, TimeUnit.HOURS) // or no expiry if lookup table is static
                .recordStats());

        // ✅ Dashboard-specific caches
        specs.put("dashboardMeterSummary", Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats());

        specs.put("dashboardCommunicationLogs", Caffeine.newBuilder()
                .maximumSize(50)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats());

        specs.put("dashboardSchedulerRate", Caffeine.newBuilder()
                .maximumSize(20)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats());

        specs.put("dashboardCommunicationReport", Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats());

        // ✅ Define manager supporting async cache
        CaffeineCacheManager manager = new CaffeineCacheManager() {
            @Override
            protected Cache createCaffeineCache(String name) {
                Caffeine<Object, Object> cacheSpec = specs.getOrDefault(name,
                        Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES));

                // ✅ Use buildAsync() instead of buildAsyncMap()
                AsyncCache<Object, Object> asyncCache = cacheSpec.buildAsync();

                return new CaffeineCache(name, asyncCache, true); // "true" => allow sync lookup
            }
        };
        manager.setAsyncCacheMode(true); // ✅ Important for CompletableFuture support
        return manager;
    }


}
