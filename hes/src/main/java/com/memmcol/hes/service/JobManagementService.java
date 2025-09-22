package com.memmcol.hes.service;

import com.memmcol.hes.model.SchedulerJobInfo;
import com.memmcol.hes.repository.SchedulerRepository;
import lombok.RequiredArgsConstructor;
import org.quartz.*;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
                schedulerRepository.findByJobNameAndJobGroup(jobName, jobGroup).ifPresent(jobInfo -> {
                    jobInfo.setJobStatus("RUN_NOW");
                    jobInfo.setLastRunTime(LocalDateTime.now());
                    schedulerRepository.save(jobInfo);
                });

            } catch (Exception e) {
                throw new SchedulerException("Failed to run job immediately: " + e.getMessage(), e);
            }
        }
    }

