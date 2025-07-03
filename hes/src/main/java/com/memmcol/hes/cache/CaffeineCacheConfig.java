package com.memmcol.hes.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
                .expireAfterWrite(60, TimeUnit.MINUTES)
                .recordStats());

        // Custom cache manager with per-cache Caffeine config
        return new CaffeineCacheManager() {
            @Override
            protected Cache createCaffeineCache(String name) {
                Caffeine<Object, Object> cacheSpec = specs.getOrDefault(name,
                        Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES));
                return new CaffeineCache(name, cacheSpec.build());
            }
        };
    }
}
