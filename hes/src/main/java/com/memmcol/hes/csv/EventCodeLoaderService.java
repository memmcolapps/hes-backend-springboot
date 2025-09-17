package com.memmcol.hes.csv;

import com.memmcol.hes.entities.EventCodeLookup;
import com.memmcol.hes.entities.EventType;
import com.memmcol.hes.repository.EventCodeLookupRepository;
import com.memmcol.hes.repository.EventTypeRepository;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.exceptions.CsvException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileReader;
import java.io.Reader;
import java.util.List;

@Service
@Slf4j
public class EventCodeLoaderService {

    private final EventCodeLookupRepository repo;
    private final EventTypeRepository eventTypeRepo;
    private final EventCodeCsvRowSaver rowSaver;

    public EventCodeLoaderService(EventCodeLookupRepository repo, EventTypeRepository eventTypeRepo, EventCodeCsvRowSaver rowSaver) {
        this.repo = repo;
        this.eventTypeRepo = eventTypeRepo;
        this.rowSaver = rowSaver;
    }

    public void loadCsv(String filePath) throws Exception {
        int inserted = 0;
        int skipped = 0;
        int failed = 0;

        try (Reader reader = new FileReader(filePath)) {
            CsvToBean<EventCodeCsvDTO> csvToBean = new CsvToBeanBuilder<EventCodeCsvDTO>(reader)
                    .withType(EventCodeCsvDTO.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .withIgnoreEmptyLine(true)
                    .withThrowExceptions(false)
                    .build();

            List<EventCodeCsvDTO> rows = csvToBean.parse();

            if (!csvToBean.getCapturedExceptions().isEmpty()) {
                for (CsvException e : csvToBean.getCapturedExceptions()) {
                    log.error("⚠️ CSV parse error at line {} : {}", e.getLineNumber(), e.getMessage());
                    failed++;
                }
            }

            int line = 1;
            for (EventCodeCsvDTO dto : rows) {
                RowResult result = rowSaver.saveRow(dto, line);
                if (result.isSuccess()) inserted++;
                else if (result.isSkipped()) skipped++;
                else failed++;
                line++;
            }
        }

        log.info("\n===== CSV Import Summary =====");
        log.info("✅ Inserted: {}", inserted);
        log.info("⏭ Skipped (duplicates): {}", skipped);
        log.info("❌ Failed: {}", failed);
        log.info("==============================\n");
    }
}
