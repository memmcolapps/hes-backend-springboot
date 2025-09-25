package com.memmcol.hes.controller;

import java.util.List;
import java.util.Map;

import com.memmcol.hes.schedulers.QuartzJobStatusService;
import com.memmcol.hes.model.Message;
import com.memmcol.hes.model.SchedulerJobInfo;
import com.memmcol.hes.schedulers.SchedulerJobServiceQuartz;
import com.memmcol.hes.service.JobManagementService;
import org.quartz.SchedulerException;
import org.quartz.SchedulerMetaData;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/job")
public class JobController {

    /*
    * TODO:
    *  1. Delete this controller.
    *  2. Delete JobIndexControler
    *  3. Deleet Job Management Controller*/

    private final SchedulerJobServiceQuartz scheduleJobService;
    private final QuartzJobStatusService jobStatusService;
    private final JobManagementService jobManagementService;

    // Create or update a job
    @PostMapping("/saveOrUpdate")
    public ResponseEntity<Message> saveOrUpdate(@RequestBody SchedulerJobInfo job) {
        log.info("saveOrUpdate params: {}", job);
        Message message = Message.failure();
        try {
            scheduleJobService.scheduleNewJob(job);
            message = Message.success();
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            log.error("saveOrUpdate error:", e);
            message.setMsg(e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
        }
    }

    // Get Quartz scheduler metadata
    @GetMapping("/metaData")
    public ResponseEntity<SchedulerMetaData> metaData() throws SchedulerException {
        SchedulerMetaData metaData = scheduleJobService.getMetaData();
        return ResponseEntity.ok(metaData);
    }

    // Get all jobs from scheduler_job_info table
    @GetMapping("/getAllJobs")
    public ResponseEntity<List<SchedulerJobInfo>> getAllJobs() {
        List<SchedulerJobInfo> jobList = scheduleJobService.getAllJobList();
        return ResponseEntity.ok(jobList);
    }

    // Trigger a job immediately
    @PostMapping("/runJob")
    public ResponseEntity<Message> runJob(@RequestBody SchedulerJobInfo job) {
        log.info("runJob params: {}", job);
        Message message = Message.failure();
        try {
            scheduleJobService.startJobNow(job);
            message = Message.success();
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            log.error("runJob error:", e);
            message.setMsg(e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
        }
    }

    // Pause a job
    @PostMapping("/pauseJob")
    public ResponseEntity<Message> pauseJob(@RequestBody SchedulerJobInfo job) {
        log.info("pauseJob params: {}", job);
        Message message = Message.failure();
        try {
            scheduleJobService.pauseJob(job);
            message = Message.success();
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            log.error("pauseJob error:", e);
            message.setMsg(e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
        }
    }

    // Resume a paused job
    @PostMapping("/resumeJob")
    public ResponseEntity<Message> resumeJob(@RequestBody SchedulerJobInfo job) {
        log.info("resumeJob params: {}", job);
        Message message = Message.failure();
        try {
            scheduleJobService.resumeJob(job);
            message = Message.success();
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            log.error("resumeJob error:", e);
            message.setMsg(e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
        }
    }

    // Delete a job
    @PostMapping("/deleteJob")
    public ResponseEntity<Message> deleteJob(@RequestBody SchedulerJobInfo job) {
        log.info("deleteJob params: {}", job);
        Message message = Message.failure();
        try {
            scheduleJobService.deleteJob(job);
            message = Message.success();
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            log.error("deleteJob error:", e);
            message.setMsg(e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
        }
    }

    @PutMapping("/update")
    public ResponseEntity<String> updateJob(@RequestBody SchedulerJobServiceQuartz.JobUpdateRequest request) {
        boolean success = scheduleJobService.updateJobCron(request);
        return success
                ? ResponseEntity.ok("Job updated successfully")
                : ResponseEntity.badRequest().body("Failed to update job");
    }

    // New: Get job status (RUNNING, PAUSED, COMPLETED)
    @GetMapping("/quartz/status")
    public ResponseEntity<Message> getJobStatus(@RequestParam String jobName, @RequestParam String jobGroup) {
        Message message = Message.failure();
        try {
            String status = jobStatusService.getJobStatus(jobName, jobGroup);
            message = Message.success();
            message.setMsg(status);
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            log.error("getJobStatus error:", e);
            message.setMsg(e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
        }
    }

    @GetMapping("/quartz/status/all")
    public Map<String, String> getAllJobStatuses() throws SchedulerException {
        return jobStatusService.getAllJobStatuses();
    }

    @PostMapping("/quartz/pause/{jobGroup}/{jobName}")
    public String pauseJob(
            @PathVariable String jobGroup,
            @PathVariable String jobName) throws SchedulerException {
        jobStatusService.pauseJob(jobName, jobGroup);
        return "Job paused: " + jobGroup + "." + jobName;
    }

    @PostMapping("/quartz/resume/{jobGroup}/{jobName}")
    public String resumeJob(
            @PathVariable String jobGroup,
            @PathVariable String jobName) throws SchedulerException {
        jobStatusService.resumeJob(jobName, jobGroup);
        return "Job resumed: " + jobGroup + "." + jobName;
    }

    @DeleteMapping("/quartz/delete/{jobGroup}/{jobName}")
    public String deleteJob(
            @PathVariable String jobGroup,
            @PathVariable String jobName) throws SchedulerException {
        jobStatusService.deleteJob(jobName, jobGroup);
        return "Job deleted: " + jobGroup + "." + jobName;
    }

    @PostMapping("/quartz/trigger/{jobGroup}/{jobName}")
    public String triggerNow(
            @PathVariable String jobGroup,
            @PathVariable String jobName) throws SchedulerException {
        jobStatusService.triggerNow(jobName, jobGroup);
        return "Job triggered immediately: " + jobGroup + "." + jobName;
    }

}
