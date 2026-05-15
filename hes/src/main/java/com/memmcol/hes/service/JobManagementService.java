package com.memmcol.hes.service;

import com.memmcol.hes.model.SchedulerJobInfo;
import com.memmcol.hes.repository.SchedulerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Deprecated
public class JobManagementService {

    private final SchedulerFactoryBean schedulerFactoryBean;
    private final SchedulerRepository schedulerRepository;

    private Scheduler getScheduler() {
        return schedulerFactoryBean.getScheduler();
    }

    // ✅ DTO for job status response
    public record JobStatusResponse(
            String jobName,
            String jobGroup,
            String quartzState,
            String dbStatus,
            LocalDateTime lastRunTime,
            String cronExpression
    ) {}

    // ✅ Pause job
    public void pauseJob(String jobName, String jobGroup) throws SchedulerException {
        Scheduler scheduler = getScheduler();
        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
        scheduler.pauseJob(jobKey);

        schedulerRepository.findByJobNameAndJobGroup(jobName, jobGroup).ifPresent(jobInfo -> {
            jobInfo.setJobStatus("PAUSED");
            schedulerRepository.save(jobInfo);
        });
    }

    // ✅ Resume job
    public void resumeJob(String jobName, String jobGroup) throws SchedulerException {
        Scheduler scheduler = getScheduler();
        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
        scheduler.resumeJob(jobKey);

        schedulerRepository.findByJobNameAndJobGroup(jobName, jobGroup).ifPresent(jobInfo -> {
            jobInfo.setJobStatus("RESUMED");
            schedulerRepository.save(jobInfo);
        });
    }

    // ✅ Delete job
    public void deleteJob(String jobName, String jobGroup) throws SchedulerException {
        Scheduler scheduler = getScheduler();
        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
        scheduler.deleteJob(jobKey);

        schedulerRepository.findByJobNameAndJobGroup(jobName, jobGroup).ifPresent(schedulerRepository::delete);
    }

    // ✅ Update CRON schedule
    public void updateJobCron(String jobName, String jobGroup, String newCron) throws SchedulerException {
        Scheduler scheduler = getScheduler();
        TriggerKey triggerKey = TriggerKey.triggerKey(jobName + "Trigger", jobGroup);

        CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(newCron);
        Trigger newTrigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .withSchedule(scheduleBuilder)
                .build();

        scheduler.rescheduleJob(triggerKey, newTrigger);

        schedulerRepository.findByJobNameAndJobGroup(jobName, jobGroup).ifPresent(jobInfo -> {
            jobInfo.setCronExpression(newCron);
            jobInfo.setJobStatus("UPDATED");
            schedulerRepository.save(jobInfo);
        });
    }

    // ✅ List all jobs in DB
    public List<SchedulerJobInfo> getAllJobs() {
        return schedulerRepository.findAll();
    }

    // ✅ List currently running jobs
    public List<String> getRunningJobs() throws SchedulerException {
        Scheduler scheduler = getScheduler();
        List<JobExecutionContext> executingJobs = scheduler.getCurrentlyExecutingJobs();

        return executingJobs.stream()
                .map(context -> context.getJobDetail().getKey().toString())
                .toList();
    }

    // ✅ Get job status from Quartz + DB
    public JobStatusResponse getJobStatus(String jobName, String jobGroup) throws SchedulerException {
        Scheduler scheduler = getScheduler();
        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);

        String quartzState;
        String cronExpression;

        if (scheduler.checkExists(jobKey)) {
            List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
            if (!triggers.isEmpty() && triggers.get(0) instanceof CronTrigger cronTrigger) {
                Trigger.TriggerState state = scheduler.getTriggerState(cronTrigger.getKey());
                quartzState = state.name();
                cronExpression = cronTrigger.getCronExpression();
            } else {
                cronExpression = null;
                quartzState = "UNKNOWN";
            }
        } else {
            cronExpression = null;
            quartzState = "UNKNOWN";
        }

        return schedulerRepository.findByJobNameAndJobGroup(jobName, jobGroup)
                .map(jobInfo -> new JobStatusResponse(
                        jobInfo.getJobName(),
                        jobInfo.getJobGroup(),
                        quartzState,
                        jobInfo.getJobStatus(),
                        jobInfo.getLastRunTime(),
                        cronExpression
                ))
                .orElse(new JobStatusResponse(
                        jobName,
                        jobGroup,
                        quartzState,
                        "NOT_FOUND",
                        null,
                        cronExpression
                ));
    }


    // ✅ Run a job immediately and log it
    public void runJobNow(String jobName, String jobGroup) throws SchedulerException {
            Scheduler scheduler = getScheduler();
            JobKey jobKey = JobKey.jobKey(jobName, jobGroup);

            if (!scheduler.checkExists(jobKey)) {
                throw new SchedulerException("Job not found: " + jobGroup + "/" + jobName);
            }

            try {
                // ✅ Trigger job immediately (creates temporary trigger in Quartz DB)
                scheduler.triggerJob(jobKey);

                // ✅ Log execution in your audit table, not Quartz triggers
                Optional<SchedulerJobInfo> row = schedulerRepository.findByJobNameAndJobGroup(jobName, jobGroup);
                if (row.isPresent()) {
                    SchedulerJobInfo jobInfo = row.get();
                    jobInfo.setJobStatus("RUN_NOW");
                    jobInfo.setLastRunTime(LocalDateTime.now());
                    schedulerRepository.save(jobInfo);
                    log.info(
                            "Successfully saved {} record(s) to database (table=scheduler_job_info, operation=UPDATE, purpose=manual trigger-now metadata). job={} jobGroup={} hes.quartz.catalog phase=SCHEDULE_META_UPDATE",
                            1, jobName, jobGroup);
                } else {
                    log.warn(
                            "No records saved to database (table=scheduler_job_info, operation=UPDATE, purpose=manual trigger-now metadata). recordsSaved=0. reason=no_matching_catalog_row. job={} jobGroup={} hes.quartz.catalog phase=SCHEDULE_META_UPDATE_SKIPPED",
                            jobName, jobGroup);
                }

            } catch (Exception e) {
                log.error(
                        "Error saving record(s) to database or triggering Quartz job (runJobNow). table=scheduler_job_info (if reached). recordsSaved=0. job={} jobGroup={}. cause={}",
                        jobName, jobGroup, e.getMessage(), e);
                throw new SchedulerException("Failed to run job immediately: " + e.getMessage(), e);
            }
        }
    }

