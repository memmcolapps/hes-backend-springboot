package com.memmcol.hes.controller;

import com.memmcol.hes.model.SchedulerJobInfo;
import com.memmcol.hes.service.JobManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.quartz.SchedulerException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v2/jobs")
@RequiredArgsConstructor
@Tag(name = "Job Management", description = "APIs to manage Quartz jobs")
public class JobManagementController {

    private final JobManagementService jobManagementService;

    public record UpdateCronRequest(
            String jobGroup,
            String jobName,
            String cron
    ) {}

    // ✅ DTO for on-demand job trigger
    public record RunNowRequest(
            String jobGroup,
            String jobName
    ) {}


    // ✅ Pause job
    @PostMapping("/{jobGroup}/{jobName}/pause")
    @Operation(summary = "Pause a job", description = "Pause a Quartz job by name and group")
    public ResponseEntity<String> pauseJob(
            @Parameter(description = "Job group", example = "profiles") @PathVariable String jobGroup,
            @Parameter(description = "Job name", example = "Channel1Job") @PathVariable String jobName
    ) throws SchedulerException {
        jobManagementService.pauseJob(jobName, jobGroup);
        return ResponseEntity.ok("Job paused: " + jobGroup + "/" + jobName);
    }

    // ✅ Resume job
    @PostMapping("/{jobGroup}/{jobName}/resume")
    @Operation(summary = "Resume a job", description = "Resume a paused Quartz job by name and group")
    public ResponseEntity<String> resumeJob(
            @Parameter(description = "Job group", example = "profiles") @PathVariable String jobGroup,
            @Parameter(description = "Job name", example = "Channel1Job") @PathVariable String jobName
    ) throws SchedulerException {
        jobManagementService.resumeJob(jobName, jobGroup);
        return ResponseEntity.ok("Job resumed: " + jobGroup + "/" + jobName);
    }

    // ✅ Delete job
    @DeleteMapping("/{jobGroup}/{jobName}")
    @Operation(summary = "Delete a job", description = "Remove a job completely from Quartz and DB")
    public ResponseEntity<String> deleteJob(
            @Parameter(description = "Job group", example = "profiles") @PathVariable String jobGroup,
            @Parameter(description = "Job name", example = "Channel1Job") @PathVariable String jobName
    ) throws SchedulerException {
        jobManagementService.deleteJob(jobName, jobGroup);
        return ResponseEntity.ok("Job deleted: " + jobGroup + "/" + jobName);
    }

    // ✅ Update CRON expression
    @PutMapping("/cron")
    @Operation(summary = "Update job CRON expression", description = "Change the CRON schedule of an existing job")
    public ResponseEntity<String> updateJobCron(@RequestBody UpdateCronRequest request) throws SchedulerException {
        jobManagementService.updateJobCron(request.jobName(), request.jobGroup(), request.cron());
        return ResponseEntity.ok(
                "Job CRON updated: " + request.jobGroup() + "/" + request.jobName() + " -> " + request.cron()
        );
    }

    // ✅ List all jobs (from DB)
    @GetMapping
    @Operation(summary = "List all jobs", description = "Fetch all jobs from DB with their metadata")
    public ResponseEntity<List<SchedulerJobInfo>> getAllJobs() {
        return ResponseEntity.ok(jobManagementService.getAllJobs());
    }

    // ✅ List currently running jobs
    @GetMapping("/running")
    @Operation(summary = "List running jobs", description = "Fetch all jobs currently running in Quartz scheduler")
    public ResponseEntity<List<String>> getRunningJobs() throws SchedulerException {
        return ResponseEntity.ok(jobManagementService.getRunningJobs());
    }


    // ✅ Get job status
    @GetMapping("/{jobGroup}/{jobName}/status")
    @Operation(summary = "Get job status", description = "Fetch the status of a scheduled job from Quartz and DB")
    public ResponseEntity<JobManagementService.JobStatusResponse> getJobStatus(
            @PathVariable String jobGroup,
            @PathVariable String jobName) {

        try {
            JobManagementService.JobStatusResponse status = jobManagementService.getJobStatus(jobName, jobGroup);
            return ResponseEntity.ok(status);
        } catch (SchedulerException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ✅ Trigger job on-demand
    @PostMapping("/run")
    @Operation(summary = "Run job on-demand", description = "Manually trigger a scheduled job immediately")
    public ResponseEntity<String> runJobNow(@RequestBody RunNowRequest request) {
        try {
            jobManagementService.runJobNow(request.jobName(), request.jobGroup());
            return ResponseEntity.ok("Job executed on-demand: "
                    + request.jobGroup() + "/" + request.jobName());
        } catch (SchedulerException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to run job: " + e.getMessage());
        }
    }




}