package com.memmcol.hes.controller;

import com.memmcol.hes.service.ObisMappingImportService;
import com.memmcol.hes.model.ObisMapping;
import com.memmcol.hes.service.ObisScalerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/obis")
@RequiredArgsConstructor
public class ObisMappingController {

    private final ObisMappingImportService importService;
    private final ObisScalerService scalerService;

    @PostMapping("/import-from-file/{model}")
    public ResponseEntity<?> importObisMappings(@PathVariable String model,
            @RequestParam(defaultValue = "false") boolean hexNotation) {
        try {
            Map<String, Object> response = importService.importFromCsvFile(model, hexNotation);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Import failed: " + e.getMessage());
        }
    }

    @PostMapping("/update-scaler-unit/{meterModel}/{meterSerial}")
    public ResponseEntity<?> getObisMappingForMeter(
            @PathVariable String meterSerial,
            @PathVariable String meterModel) {
        try {
            Map<String, Object> mapping = scalerService.updateScalerUnitForMeter(meterSerial, meterModel);

            if (mapping.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("No OBIS mapping found for meter serial: " + meterSerial);
            }
            return ResponseEntity.ok(mapping);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving OBIS mapping: " + ex.getMessage());
        }
    }
}