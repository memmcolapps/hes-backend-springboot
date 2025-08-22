package com.memmcol.hes.service;

import com.memmcol.hes.model.MeterRatioModel;
import com.memmcol.hes.repository.MeterRatioRepository;
import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@AllArgsConstructor
public class MeterRatioCacheService {

    private final MeterRatioRepository meterRatioRepository;

    /**
     * Step 1: Try to get from cache
     */
    @Cacheable(cacheNames = "meterRatios", key = "#meterSerial")
    public Optional<MeterRatioModel> getMeterRatio(String meterSerial) {
        return meterRatioRepository.findByMeterSerial(meterSerial);
    }

    /**
     * Step 2: Manually fetch from DB only (bypass cache)
     */
    public Optional<MeterRatioModel> findInDbOnly(String meterSerial) {
        return meterRatioRepository.findByMeterSerial(meterSerial);
    }

    /**
     * Step 3: Save to DB and update cache
     */
    @CachePut(cacheNames = "meterRatios", key = "#meterSerial")
    public MeterRatioModel saveAndUpdateCache(String meterSerial, Integer ctRatio, Integer ptRatio, Integer ctptRatio) {
        MeterRatioModel record = meterRatioRepository
                .findByMeterSerial(meterSerial)
                .orElse(new MeterRatioModel());

        record.setMeterSerial(meterSerial);
        record.setCtRatio(ctRatio);
        record.setPtRatio(ptRatio);
        record.setCtptRatio(ctptRatio);
        record.setReadTime(LocalDateTime.now());

        return meterRatioRepository.save(record);
    }

    /**
     * Optional: Only update cache (if you already have a DB record)
     */
    @CachePut(cacheNames = "meterRatios", key = "#record.meterSerial")
    public MeterRatioModel updateCache(MeterRatioModel record) {
        return record;
    }
}