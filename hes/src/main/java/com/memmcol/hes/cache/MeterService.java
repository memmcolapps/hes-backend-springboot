package com.memmcol.hes.cache;

import com.memmcol.hes.model.ObisMapping;
import com.memmcol.hes.repository.MeterRepository;
import org.springframework.cache.annotation.Cacheable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeterService {
    private final MeterRepository meterRepository;

    @CachePut(value = "obisMappings", key = "#result.obisCode")
    public ObisMapping updateMapping(ObisMapping result) {
        return meterRepository.save(result);
    }

    @CacheEvict(value = "obisMappings", key = "#obisCode")
    public void evictMapping(String obisCode) {
        log.info("Obis mapping evicted: {}", obisCode);
    }

    //Test methods
    @Cacheable(value = "obisMappings", key = "#obisCode")
    public ObisMapping getMapping(String obisCode) {
        try {
//            simulateLatency(); // Simulate slow DB call for cache test
            return meterRepository.findByObisCode(obisCode);
        } catch (Exception e) {
            log.error("❌ Failed to fetch ObisMapping: {}", e.getMessage(), e);
//            return new ObisMapping("0000", "fallback");
            return new ObisMapping();
        }
    }

    private void simulateLatency() {
        try {
            Thread.sleep(2000); // simulate DB/network delay
        } catch (InterruptedException ignored) {}
    }

}
