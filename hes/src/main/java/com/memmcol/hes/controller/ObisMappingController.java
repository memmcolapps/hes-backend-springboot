package com.memmcol.hes.controller;

import com.memmcol.hes.service.ObisMappingImportService;
import com.memmcol.hes.model.ObisMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/obis")
@RequiredArgsConstructor
public class ObisMappingController {

    private final ObisMappingImportService importService;

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
}