package com.memmcol.hes.controller;

import com.memmcol.hes.model.SchedulerJobInfo;
import com.memmcol.hes.schedulers.QuartzJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/quartz")
@RequiredArgsConstructor
public class QuartzJobController {
    private final QuartzJobService quartzJobService;

    // 4. Update job interval using CRON (expects JSON payload)
    public record UpdateCronRequest(
            String jobGroup,
            String jobName,
            String cron
    ) {}

    public record UpdateObisRequest(
            String jobName,
            String jobGroup,
            String obisCodes // comma-separated string, e.g. "1.0.99.1.0.255,1.0.99.2.0.255"
    ) {}

    // ---------------- META ----------------
    @GetMapping("/meta")
    public ResponseEntity<?> getMetaData() {
        try {
            return ResponseEntity.ok(quartzJobService.getMetaData());
        } catch (Exception e) {
            log.error("❌ Error fetching Quartz metadata", e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @GetMapping("/jobs/db")
    public ResponseEntity<List<SchedulerJobInfo>> getAllJobsFromDb() {
        return ResponseEntity.ok(quartzJobService.getAllJobsFromDb());
    }

    @GetMapping("/jobs/quartz")
    public ResponseEntity<?> getAllJobsFromQuartz() {
        try {
            Map<String, String> jobs = quartzJobService.getAllJobsFromQuartz();
            return ResponseEntity.ok(jobs);
        } catch (Exception e) {
            log.error("❌ Error fetching Quartz jobs", e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    // ---------------- STATUS ----------------
    @GetMapping("/status/{jobGroup}/{jobName}")
    public ResponseEntity<?> getJobStatus(
            @PathVariable String jobName,
            @PathVariable String jobGroup) {
        try {
            return ResponseEntity.ok(quartzJobService.getJobStatus(jobName, jobGroup));
        } catch (Exception e) {
            log.error("❌ Error fetching job status for {}", jobName, e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    // ---------------- CRUD ----------------
    @PostMapping("/jobs")
    public ResponseEntity<?> createOrUpdateJob(@RequestBody SchedulerJobInfo jobInfo) {
        try {
            quartzJobService.saveOrUpdate(jobInfo);
            return ResponseEntity
                    .status(HttpStatus.CREATED)   // or HttpStatus.OK if it's update
                    .body("Job successfully created or updated.");
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error creating/updating job: " + e.getMessage());
        }
    }

    @DeleteMapping("/jobs/{jobGroup}/{jobName}")
    public ResponseEntity<?> deleteJob(
            @PathVariable String jobName,
            @PathVariable String jobGroup) {
        boolean deleted = quartzJobService.deleteJob(jobName, jobGroup);
        return deleted ? ResponseEntity.ok("🗑️ Job deleted successfully.")
                : ResponseEntity.badRequest().body("❌ Failed to delete job.");
    }

    // ---------------- CONTROL ----------------
    @PostMapping("/pause/{jobGroup}/{jobName}")
    public ResponseEntity<?> pauseJob(
            @PathVariable String jobName,
            @PathVariable String jobGroup) {
        boolean paused = quartzJobService.pauseJob(jobName, jobGroup);
        return paused ? ResponseEntity.ok("⏸️ Job paused successfully.")
                : ResponseEntity.badRequest().body("❌ Failed to pause job.");
    }

    @PostMapping("/resume/{jobGroup}/{jobName}")
    public ResponseEntity<?> resumeJob(
            @PathVariable String jobName,
            @PathVariable String jobGroup) {
        boolean resumed = quartzJobService.resumeJob(jobName, jobGroup);
        return resumed ? ResponseEntity.ok("▶️ Job resumed successfully.")
                : ResponseEntity.badRequest().body("❌ Failed to resume job.");
    }

    @PostMapping("/trigger/{jobGroup}/{jobName}")
    public ResponseEntity<?> triggerNow(
            @PathVariable String jobName,
            @PathVariable String jobGroup) {
        boolean triggered = quartzJobService.triggerNow(jobName, jobGroup);
        return triggered ? ResponseEntity.ok("⚡ Job triggered now.")
                : ResponseEntity.badRequest().body("❌ Failed to trigger job.");
    }

    // ---------------- Update job intervals ----------------
    @PostMapping("/{jobGroup}/{jobName}/interval/seconds")
    public ResponseEntity<?> seconds(@PathVariable String jobGroup, @PathVariable String jobName,
                                     @RequestParam int seconds) {
        boolean ok = quartzJobService.updateJobIntervalSeconds(jobName, jobGroup, seconds);
        return ok ? ResponseEntity.ok("Updated") : ResponseEntity.status(500).body("Failed");
    }

    @PostMapping("/{jobGroup}/{jobName}/interval/minutes")
    public ResponseEntity<?> minutes(@PathVariable String jobGroup, @PathVariable String jobName,
                                     @RequestParam int minutes) {
        boolean ok = quartzJobService.updateJobIntervalMinutes(jobName, jobGroup, minutes);
        return ok ? ResponseEntity.ok("Updated") : ResponseEntity.status(500).body("Failed");
    }

    @PostMapping("/{jobGroup}/{jobName}/interval/hours")
    public ResponseEntity<?> hours(@PathVariable String jobGroup, @PathVariable String jobName,
                                   @RequestParam int hours) {
        boolean ok = quartzJobService.updateJobIntervalHours(jobName, jobGroup, hours);
        return ok ? ResponseEntity.ok("Updated") : ResponseEntity.status(500).body("Failed");
    }

    @PostMapping("/interval/cron")
    public ResponseEntity<?> cron(@RequestBody UpdateCronRequest dto) {
        boolean ok = quartzJobService.updateJobCron(dto.jobName(), dto.jobGroup(), dto.cron());
        return ok ? ResponseEntity.ok("Updated") : ResponseEntity.status(500).body("Failed");
    }

    @PostMapping("/obis")
    public ResponseEntity<?> updateObisCodes(@RequestBody UpdateObisRequest dto) {
        log.info("Received updateObisCodes: {}", dto);
        boolean ok = quartzJobService.updateJobObisCodes(dto.jobName(), dto.jobGroup(), dto.obisCodes());
        return ok
                ? ResponseEntity.ok("OBIS codes updated successfully")
                : ResponseEntity.status(500).body("Failed to update OBIS codes");
    }

}
