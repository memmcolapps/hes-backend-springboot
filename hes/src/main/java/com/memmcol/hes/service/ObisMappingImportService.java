package com.memmcol.hes.service;

import com.memmcol.hes.model.ObisMapping;
import com.memmcol.hes.repository.ObisMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ObisMappingImportService {
    private final ObisMappingRepository obisMappingRepository;

    private static final String CSV_FILE_PATH = "./obis_mapping.csv";

    public Map<String, Object> importFromCsvFile(String model, boolean hexNotation) throws IOException {
        List<ObisMapping> successfulImports = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;
        File file = new File(CSV_FILE_PATH);
        String line = "";

        if (!file.exists()) {
            log.error("CSV file not found: {} ", CSV_FILE_PATH);
            Map<String, Object> result = new HashMap<>();
            result.put("error", "CSV file not found: {} "+ CSV_FILE_PATH);
            result.put("successful_count", successCount);
            result.put("failed_count", failureCount);
            result.put("successful_imports", Collections.EMPTY_LIST);
            return result;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
//            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false; // skip header
                    continue;
                }

                String[] columns = line.split(",", -1); // keep empty strings

                if (columns.length < 9) continue;

                String obisCodeCombined;
                int classId, attributeIndex;

                if (hexNotation) {
                    // columns[3] = class_id (e.g. "01"), columns[4] = hex OBIS code (e.g. "0100630100FF")
                    classId = Integer.parseInt(columns[3].trim());
                    String dotNotation = hexToDotNotation(columns[4].trim());
                    attributeIndex = Integer.parseInt(columns[5].trim());
                    obisCodeCombined = classId + ";" + dotNotation + ";" + attributeIndex + ";0";
                } else {
                    classId = Integer.parseInt(columns[3].trim());
                    attributeIndex = Integer.parseInt(columns[5].trim());
                    obisCodeCombined = classId + ";" + columns[4].trim() + ";" + attributeIndex + ";0";
                }

                boolean exists = obisMappingRepository.existsByModelAndObisCodeCombined(model, obisCodeCombined);
                if (exists) {
                    log.warn("Skipping duplicate entry for model={}, obis_code_combined={}", model, obisCodeCombined);
                    failureCount++;
                    continue;
                }

                ObisMapping obis = new ObisMapping();
                obis.setClassId(classId);
                obis.setModel(model);
                obis.setObisCode(hexNotation ? hexToDotNotation(columns[4].trim()) : columns[4].trim());
                obis.setAttributeIndex(attributeIndex);
                obis.setDataIndex(0);
                obis.setObisCodeCombined(obisCodeCombined);
                obis.setDataType(columns[6].trim());
                obis.setDescription(columns[2].trim());
                obis.setGroupName(columns[1].trim());

                obis.setScaler(columns[7].isBlank() ? null : Double.parseDouble(columns[7].trim()));
                obis.setUnit(columns.length > 8 ? columns[8].trim() : null);

                obisMappingRepository.save(obis);
                successfulImports.add(obis);
                successCount++;
            }
        } catch (IOException ex) {
            log.error("Error reading CSV on line {} : {}", line, ex.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("Error: ", "Error reading CSV on line " + line + " : " + ex.getMessage());
            result.put("successful_count", successCount);
            result.put("failed_count", failureCount);
            result.put("successful_imports", successfulImports);
            return result;
        } catch (Exception e) {
            log.error("Error parsing CSV on line {} : {}", line, e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("Error: ", "Error parsing CSV on line " + line + " : " + e.getMessage());
            result.put("successful_count", successCount);
            result.put("failed_count", failureCount);
            result.put("successful_imports", successfulImports);
            return result;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("successful", "CSV Extracted successfully");
        result.put("successful_count", successCount);
        result.put("failed_count", failureCount);
        result.put("successful_imports", successfulImports);

        return result;
    }

    /**
     * Convert hex OBIS notation (e.g., "0100630100FF") to dot notation (e.g., "1.0.99.1.0.255")
     */
    private String hexToDotNotation(String hex) {
        if (hex.length() != 12) throw new IllegalArgumentException("Hex OBIS code must be 12 characters");

        List<String> parts = new ArrayList<>();
        for (int i = 0; i < 12; i += 2) {
            String byteStr = hex.substring(i, i + 2);
            int part = Integer.parseInt(byteStr, 16);
            parts.add(String.valueOf(part));
        }
        return String.join(".", parts);
    }

}
