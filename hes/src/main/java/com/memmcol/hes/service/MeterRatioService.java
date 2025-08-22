package com.memmcol.hes.service;

import com.memmcol.hes.domain.profile.MeterRatios;
import com.memmcol.hes.domain.profile.ObisMappingService;
import com.memmcol.hes.domain.profile.ReadCTPT;
import com.memmcol.hes.model.MeterRatioModel;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@AllArgsConstructor
public class MeterRatioService {
    private final ObisMappingService obisService;
    private final MeterRatioCacheService ratioCacheService;
    private final ReadCTPT readCTPT;

    public MeterRatios readMeterRatios(String model, String meterSerial) throws Exception {
        // Step 1: Try cache
        Optional<MeterRatioModel> cached = ratioCacheService.getMeterRatio(meterSerial);
        if (cached.isPresent()) {
            MeterRatioModel rec = cached.get();
            return new MeterRatios(rec.getCtRatio(), rec.getPtRatio(), rec.getCtptRatio());
        }

        // Step 2: Try DB
        Optional<MeterRatioModel> dbRecord = ratioCacheService.findInDbOnly(meterSerial);
        if (dbRecord.isPresent()) {
            MeterRatioModel rec = dbRecord.get();
            ratioCacheService.updateCache(rec); // update cache manually
            return new MeterRatios(rec.getCtRatio(), rec.getPtRatio(), rec.getCtptRatio());
        }

        // Step 3: Read from meter
        return readAndSaveMeterRatios(model, meterSerial);
    }

    //Step 2: Read from the meter

    public MeterRatios readAndSaveMeterRatios(String model, String meterSerial) throws Exception {
        // Step 1: Read from the meter
        MeterRatios meterRatios = readCTPT.readMeterRatios(model, meterSerial);

        // Step 2: Save to DB and update cache
        ratioCacheService.saveAndUpdateCache(
                meterSerial,
                meterRatios.getCtRatio(),
                meterRatios.getPtRatio(),
                meterRatios.getCtptRatio()
        );
        return meterRatios;
    }

}
