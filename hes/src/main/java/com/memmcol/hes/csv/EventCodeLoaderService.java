package com.memmcol.hes.csv;

import com.memmcol.hes.entities.EventCodeLookup;
import com.memmcol.hes.entities.EventType;
import com.memmcol.hes.repository.EventCodeLookupRepository;
import com.memmcol.hes.repository.EventTypeRepository;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileReader;
import java.io.Reader;
import java.util.List;

@Service
public class EventCodeLoaderService {

    private final EventCodeLookupRepository repo;
    private final EventTypeRepository eventTypeRepo;

    public EventCodeLoaderService(EventCodeLookupRepository repo, EventTypeRepository eventTypeRepo) {
        this.repo = repo;
        this.eventTypeRepo = eventTypeRepo;
    }

    @Transactional
    public void loadCsv(String filePath) throws Exception {
        try (Reader reader = new FileReader(filePath)) {
            CsvToBean<EventCodeCsvDTO> csvToBean = new CsvToBeanBuilder<EventCodeCsvDTO>(reader)
                    .withType(EventCodeCsvDTO.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .build();

            List<EventCodeCsvDTO> rows = csvToBean.parse();

            for (EventCodeCsvDTO dto : rows) {
                EventType eventType = eventTypeRepo.findById(dto.getEventTypeId())
                        .orElseThrow(() -> new RuntimeException("EventType not found: " + dto.getEventTypeId()));

                // check for existing record
                boolean exists = repo.findByEventTypeAndCode(eventType, dto.getCode()).isPresent();
                if (exists) {
                    System.out.println("Skipping duplicate: " + dto.getEventName() + " (code " + dto.getCode() + ")");
                    continue;
                }

                EventCodeLookup entity = new EventCodeLookup();
                entity.setEventType(eventType);
                entity.setCode(dto.getCode());
                entity.setEventName(dto.getEventName());
                entity.setDescription(dto.getDescription());

                repo.save(entity);
            }
        }
    }
}
