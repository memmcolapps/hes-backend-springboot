package com.memmcol.hes.gridflex.services;

import com.memmcol.hes.model.MetersConnectionEvent;
import com.memmcol.hes.repository.MetersConnectionEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class MetersConnectionEventService {

    private final MetersConnectionEventRepository repository;

    public MetersConnectionEventService(MetersConnectionEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Updates or inserts a meter's connection event.
     *
     * @param meterNo meter number (primary key)
     * @param connectionType e.g., "Online" or "Offline"
     */
//    @Transactional
//    public void updateOrInsertEvent(String meterNo, String connectionType) {
//        Optional<MetersConnectionEvent> existingEvent = repository.findById(meterNo);
//
//        if (existingEvent.isPresent()) {
//            // Update existing record
//            MetersConnectionEvent event = existingEvent.get();
//            event.setConnectionType(connectionType);
//            event.setUpdatedAt(LocalDateTime.now());
//            repository.save(event);
//            System.out.println("✅ Updated connection event for meter: " + meterNo);
//        } else {
//            // Insert new record
//            MetersConnectionEvent newEvent = new MetersConnectionEvent();
//            newEvent.setMeterNo(meterNo);
//            newEvent.setConnectionType(connectionType);
//            newEvent.setConnectionTime(LocalDateTime.now());
//            newEvent.setUpdatedAt(LocalDateTime.now());
//            repository.save(newEvent);
//            System.out.println("✅ Created new connection event for meter: " + meterNo);
//        }
//    }

    /**
     * Fetches the last known connection event for a meter.
     */
    public Optional<MetersConnectionEvent> findByMeterNo(String meterNo) {
        return repository.findById(meterNo);
    }

//    @Transactional
//    public void updateConnectionStatus(String meterNo, String status) {
//        updateConnectionStatus(meterNo, status, LocalDateTime.now());
//    }

    @Transactional
    public void updateConnectionStatus(String meterNo, String status, LocalDateTime connectionTime) {
        repository.findById(meterNo).ifPresentOrElse(event -> {
            event.setConnectionType(status);
            event.setUpdatedAt(LocalDateTime.now());
            repository.save(event);
        }, () -> {
            MetersConnectionEvent newEvent = new MetersConnectionEvent();
            newEvent.setMeterNo(meterNo);
            newEvent.setConnectionType(status);
            newEvent.setConnectionTime(connectionTime);
            newEvent.setUpdatedAt(LocalDateTime.now());
            repository.save(newEvent);
        });
    }
}