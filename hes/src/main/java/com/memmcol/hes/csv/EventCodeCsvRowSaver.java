package com.memmcol.hes.csv;

import com.memmcol.hes.entities.EventCodeLookup;
import com.memmcol.hes.entities.EventType;
import com.memmcol.hes.repository.EventCodeLookupRepository;
import com.memmcol.hes.repository.EventTypeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class EventCodeCsvRowSaver {

    private final EventCodeLookupRepository repo;

    private final EventTypeRepository eventTypeRepo;

    public EventCodeCsvRowSaver(EventCodeLookupRepository repo, EventTypeRepository eventTypeRepo) {
        this.repo = repo;
        this.eventTypeRepo = eventTypeRepo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RowResult saveRow(EventCodeCsvDTO dto, int line) {
        try {
            EventType eventType = eventTypeRepo.findById(dto.getEventTypeId())
                    .orElseThrow(() -> new RuntimeException("EventType not found: " + dto.getEventTypeId()));

            boolean exists = repo.findByEventTypeAndCode(eventType, dto.getCode()).isPresent();
            if (exists) {
                String msg = "Skipping duplicate (line " + line + "): " +
                        dto.getEventName() + " (code " + dto.getCode() + ")";
                log.info(msg);
                return new RowResult(false, true, msg);
            }

            EventCodeLookup entity = new EventCodeLookup();
            entity.setEventType(eventType);
            entity.setCode(dto.getCode());
            entity.setEventName(dto.getEventName());
            entity.setDescription(dto.getDescription());

            repo.save(entity);

            String msg = "✅ Inserted line " + line + ": " + dto.getEventName();
            log.info(msg);
            return new RowResult(true, false, msg);

        } catch (Exception ex) {
            String msg = "❌ Error saving line " + line + ": " + ex.getMessage();
            System.err.println(msg);
            return new RowResult(false, false, msg);
        }
    }
}
