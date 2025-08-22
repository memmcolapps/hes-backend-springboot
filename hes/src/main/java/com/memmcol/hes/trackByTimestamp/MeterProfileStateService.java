package com.memmcol.hes.trackByTimestamp;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class MeterProfileStateService {

    private final MeterProfileStateRepository repository;

    public MeterProfileStateService(MeterProfileStateRepository repository) {
        this.repository = repository;
    }

    public void upsertLastTimestamp(String meterSerial, String profileObis, LocalDateTime ts) {
        int updated = repository.updateLastTimestamp(meterSerial, profileObis, ts);
        if (updated == 0) {
            MeterProfileState state = MeterProfileState.builder()
                    .meterSerial(meterSerial)
                    .profileObis(profileObis)
                    .lastTimestamp(ts)
                    .updatedAt(LocalDateTime.now())
                    .build();
            repository.save(state);
        }
    }

    public void upsertCapturePeriod(String meterSerial, String profileObis, Integer period) {
        int updated = repository.updateCapturePeriod(meterSerial, profileObis, period);
        if (updated == 0) {
            MeterProfileState state = MeterProfileState.builder()
                    .meterSerial(meterSerial)
                    .profileObis(profileObis)
                    .capturePeriodSec(period)
                    .updatedAt(LocalDateTime.now())
                    .build();
            repository.save(state);
        }
    }

    @Transactional
    public void upsertTimestampAndCapturePeriod(String serial,
                                                String obis,
                                                LocalDateTime ts,
                                                Integer periodSec) {
        int updated = repository.updateTimestampAndCapturePeriod(serial, obis, ts, periodSec);
        if (updated == 0) {
            MeterProfileState entity = MeterProfileState.builder()
                    .meterSerial(serial)
                    .profileObis(obis)
                    .lastTimestamp(ts)
                    .capturePeriodSec(periodSec)
                    .updatedAt(LocalDateTime.now())
                    .build();
            repository.save(entity);
        }
    }

    @Transactional(readOnly = true)
    public LocalDateTime getLastTimestamp(String meterSerial, String profileObis) {
        return repository.findByMeterSerialAndProfileObis(meterSerial, profileObis)
                .map(MeterProfileState::getLastTimestamp)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Integer getCapturePeriod(String meterSerial, String profileObis) {
        return repository.findByMeterSerialAndProfileObis(meterSerial, profileObis)
                .map(MeterProfileState::getCapturePeriodSec)
                .orElse(null);
    }
}