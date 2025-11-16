package com.memmcol.hes.gridflex.services;

import com.memmcol.hes.model.MetersConnectionEvent;
import com.memmcol.hes.repository.MetersConnectionEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

@Service
@Slf4j
public class MetersConnectionEventService {

    private final MetersConnectionEventRepository repository;

    public MetersConnectionEventService(MetersConnectionEventRepository repository) {
        this.repository = repository;
    }


    /**
     * Fetches the last known connection event for a meter.
     */
    public Optional<MetersConnectionEvent> findByMeterNo(String meterNo) {
        return repository.findById(meterNo);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateConnectionStatus(String meterNo, String status, LocalDateTime connectionTime) {
        log.debug("🔁 Updating connection status for meter [{}] → [{}] at {}", meterNo, status, connectionTime);
        repository.findById(meterNo.trim()).ifPresentOrElse(event -> {
            event.setConnectionType(status);
            event.setUpdatedAt(ZonedDateTime.now(ZoneId.systemDefault()).toLocalDateTime());
            if (status.equalsIgnoreCase("ONLINE")) {
                event.setOnlineTime(connectionTime);
            } else if (status.equalsIgnoreCase("OFFLINE")) {
                event.setOfflineTime(ZonedDateTime.now(ZoneId.systemDefault()).toLocalDateTime());
            }
            repository.save(event);
            log.debug("✅ Updated meter [{}] to [{}]", meterNo, status);
        }, () -> {
            log.debug("🆕 No record found for meter [{}], inserting new event.", meterNo);
            MetersConnectionEvent newEvent = new MetersConnectionEvent();
            newEvent.setMeterNo(meterNo);
            newEvent.setUpdatedAt(ZonedDateTime.now(ZoneId.systemDefault()).toLocalDateTime());
            newEvent.setConnectionType(status);
            if (status.equalsIgnoreCase("ONLINE")) {
                newEvent.setOnlineTime(connectionTime);
            } else if (status.equalsIgnoreCase("OFFLINE")) {
                newEvent.setOfflineTime(ZonedDateTime.now(ZoneId.systemDefault()).toLocalDateTime());
            }
            repository.save(newEvent);
            log.debug("✅ Inserted new meter [{}] with status [{}]", meterNo, status);
        });
    }
}