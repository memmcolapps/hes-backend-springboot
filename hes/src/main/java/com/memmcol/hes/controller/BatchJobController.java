package com.memmcol.hes.controller;

import com.memmcol.hes.dto.BulkTriggerRequest;
import com.memmcol.hes.schedulers.BulkJobExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/batch")
@RequiredArgsConstructor
public class BatchJobController {

    private final BulkJobExecutionService bulkJobExecutionService;

    @PostMapping("/read/{jobGroup}/{jobName}")
    public ResponseEntity<?> readBatch(
            @PathVariable String jobName,
            @PathVariable String jobGroup,
            @RequestBody BulkTriggerRequest request) {
        try {
            Map<String, Object> result = bulkJobExecutionService.executeBulkJob(jobGroup, jobName, request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("❌ Error executing batch job read for {}/{}", jobGroup, jobName, e);
            return ResponseEntity.badRequest().body("❌ Failed to execute batch job read: " + e.getMessage());
        }
    }

    @GetMapping("/status/{executionId}")
    public ResponseEntity<?> getBatchStatus(@PathVariable Long executionId) {
        try {
            return ResponseEntity.ok(bulkJobExecutionService.getBulkExecutionStatus(executionId));
        } catch (Exception e) {
            log.error("❌ Error fetching batch execution status for ID {}", executionId, e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
