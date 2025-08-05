package com.memmcol.hes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
public class DlmsRawDataLogger {
    private static final String BASE_DIR = "/hessystem/raw"; // Change as needed
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Save raw profile rows to JSON file per meter and timestamp.
     * @param serial Meter serial number
     * @param rows List of raw ProfileRowDTO
     * @param timestamp Base timestamp for the batch
     */
    public static void saveToFile(String serial, List<?> rows, LocalDateTime timestamp) {
        try {
            String folderPath = BASE_DIR + "/" + serial;
            Files.createDirectories(Paths.get(folderPath));

            String filename = "raw_" + TIMESTAMP_FORMAT.format(timestamp) + ".json";
            Path fullPath = Paths.get(folderPath, filename);

            try (FileWriter writer = new FileWriter(fullPath.toFile())) {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(writer, rows);
            }
            log.info("📁 Raw DLMS data saved to: {}", fullPath);
        } catch (IOException e) {
            log.error("❌ Failed to save raw DLMS data for meter {}: {}", serial, e.getMessage());
        }
    }
}
