package com.memmcol.hes.schedulers;

import com.memmcol.hes.model.SchedulerJobInfo;
import com.memmcol.hes.repository.SchedulerRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Transactional
@Service
@AllArgsConstructor
public class SchedulerJobServiceQuartz {
    private final SchedulerFactoryBean schedulerFactoryBean;
    private final SchedulerRepository schedulerRepository;

    // DTO for request
    public record JobUpdateRequest(String jobName, String jobGroup, String cronExpression) {}

    // ✅ Schedules a new job and persists once
    // ✅ Schedules a new job and persists once
    @SuppressWarnings("unchecked")
    public void scheduleNewJob(SchedulerJobInfo jobInfo) {
        try {
            Scheduler scheduler = schedulerFactoryBean.getScheduler();

            // Load job class
            Class<? extends QuartzJobBean> jobClazz =
                    (Class<? extends QuartzJobBean>) Class.forName(jobInfo.getJobClass());

            // JobDataMap (must be serializable)
            JobDataMap jobDataMap = new JobDataMap();
            jobDataMap.put("description", jobInfo.getDescription());
            jobDataMap.put("interfaceName", jobInfo.getInterfaceName());

            JobDetail jobDetail = JobBuilder.newJob(jobClazz)
                    .withIdentity(jobInfo.getJobName(), jobInfo.getJobGroup())
                    .storeDurably()
                    .usingJobData(jobDataMap)
                    .build();

            // Trigger
            Trigger trigger;

            if (jobInfo.getCronJob() != null && jobInfo.getCronJob()) {
                // Cron trigger
                trigger = TriggerBuilder.newTrigger()
                        .forJob(jobDetail)
                        .withIdentity(jobInfo.getJobName(), jobInfo.getJobGroup())
                        .withSchedule(CronScheduleBuilder.cronSchedule(jobInfo.getCronExpression())
                                .withMisfireHandlingInstructionFireAndProceed())
                        .build();
            } else {
                // Interval trigger (calculate milliseconds)
                long intervalMs = 0;
                if (jobInfo.getRepeatTime() != null && jobInfo.getRepeatTime() > 0) {
                    intervalMs = jobInfo.getRepeatTime();
                } else {
                    int seconds = jobInfo.getRepeatSeconds() != null ? jobInfo.getRepeatSeconds() : 0;
                    int minutes = jobInfo.getRepeatMinutes() != null ? jobInfo.getRepeatMinutes() : 0;
                    int hours   = jobInfo.getRepeatHours() != null ? jobInfo.getRepeatHours() : 0;
                    intervalMs = ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L;
                }

                if (intervalMs <= 0) {
                    intervalMs = 1000L; // default 1 second
                }

                trigger = TriggerBuilder.newTrigger()
                        .forJob(jobDetail)
                        .withIdentity(jobInfo.getJobName(), jobInfo.getJobGroup())
                        .startNow()
                        .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                                .withIntervalInMilliseconds(intervalMs)
                                .repeatForever()
                                .withMisfireHandlingInstructionFireNow())
                        .build();
            }

            if (!scheduler.checkExists(jobDetail.getKey())) {
                scheduler.scheduleJob(jobDetail, trigger);

                // ✅ Persist once
                jobInfo.setJobStatus("SCHEDULED");
                schedulerRepository.save(jobInfo);

                log.info("✅ Job [{}] scheduled with class={}, group={}, cronJob={}, intervalMs={}",
                        jobInfo.getJobName(), jobInfo.getJobClass(), jobInfo.getJobGroup(), jobInfo.getCronJob(),
                        jobInfo.getRepeatTime() != null ? jobInfo.getRepeatTime() : "calculated");
            } else {
                log.warn("⚠️ Job [{}] already exists. Skipping creation.", jobInfo.getJobName());
            }

        } catch (ClassNotFoundException e) {
            log.error("Class not found - {}", jobInfo.getJobClass(), e);
        } catch (SchedulerException e) {
            log.error("Scheduler exception for job [{}]: {}", jobInfo.getJobName(), e.getMessage(), e);
        }
    }

    public void updateJob(SchedulerJobInfo jobInfo) {
        try {
            Scheduler scheduler = schedulerFactoryBean.getScheduler();
            JobKey jobKey = new JobKey(jobInfo.getJobName(), jobInfo.getJobGroup());
            TriggerKey triggerKey = TriggerKey.triggerKey(jobInfo.getJobName(), jobInfo.getJobGroup());

            // Load job class
            Class<? extends QuartzJobBean> jobClazz =
                    (Class<? extends QuartzJobBean>) Class.forName(jobInfo.getJobClass());

            // JobDataMap (must be serializable)
            JobDataMap jobDataMap = new JobDataMap();
            jobDataMap.put("description", jobInfo.getDescription());
            jobDataMap.put("interfaceName", jobInfo.getInterfaceName());

            JobDetail newJobDetail = JobBuilder.newJob(jobClazz)
                    .withIdentity(jobKey)
                    .storeDurably()
                    .usingJobData(jobDataMap)
                    .build();

            // Build new trigger
            Trigger newTrigger;
            if (jobInfo.getCronJob() != null && jobInfo.getCronJob()) {
                newTrigger = TriggerBuilder.newTrigger()
                        .forJob(newJobDetail)
                        .withIdentity(triggerKey)
                        .withSchedule(CronScheduleBuilder.cronSchedule(jobInfo.getCronExpression())
                                .withMisfireHandlingInstructionFireAndProceed())
                        .build();
            } else {
                // Calculate interval in milliseconds
                long intervalMs = 0;
                if (jobInfo.getRepeatTime() != null && jobInfo.getRepeatTime() > 0) {
                    intervalMs = jobInfo.getRepeatTime();
                } else {
                    int seconds = jobInfo.getRepeatSeconds() != null ? jobInfo.getRepeatSeconds() : 0;
                    int minutes = jobInfo.getRepeatMinutes() != null ? jobInfo.getRepeatMinutes() : 0;
                    int hours   = jobInfo.getRepeatHours() != null ? jobInfo.getRepeatHours() : 0;
                    intervalMs = ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L;
                }

                if (intervalMs <= 0) {
                    intervalMs = 1000L; // default 1 second
                }

                newTrigger = TriggerBuilder.newTrigger()
                        .forJob(newJobDetail)
                        .withIdentity(triggerKey)
                        .startNow()
                        .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                                .withIntervalInMilliseconds(intervalMs)
                                .repeatForever()
                                .withMisfireHandlingInstructionFireNow())
                        .build();
            }

            if (scheduler.checkExists(jobKey)) {
                // Replace existing job and trigger
                scheduler.addJob(newJobDetail, true); // true = replace existing
                scheduler.rescheduleJob(new TriggerKey(jobInfo.getJobName(), jobInfo.getJobGroup()), newTrigger);

                log.info("✅ Job [{}] updated successfully", jobInfo.getJobName());
            } else {
                // Schedule new job
                scheduler.scheduleJob(newJobDetail, newTrigger);
                log.info("✅ Job [{}] scheduled successfully", jobInfo.getJobName());
            }

            // Persist once
            schedulerRepository.findByJobNameAndJobGroup(jobInfo.getJobName(), jobInfo.getJobGroup())
                    .ifPresent(info -> {
                        jobInfo.setJobStatus("SCHEDULED/UPDATED");
                        schedulerRepository.save(jobInfo);
                    });

        } catch (ClassNotFoundException e) {
            log.error("Class not found - {}", jobInfo.getJobClass(), e);
        } catch (SchedulerException e) {
            log.error("Scheduler exception for job [{}]: {}", jobInfo.getJobName(), e.getMessage(), e);
        }
    }

    /**
     * Update job cron expression by pausing, rescheduling, and updating DB.
     */
    public boolean updateJobCron(JobUpdateRequest request) {
        try {
            Scheduler scheduler = schedulerFactoryBean.getScheduler();
            JobKey jobKey = new JobKey(request.jobName(), request.jobGroup());

            if (!scheduler.checkExists(jobKey)) {
                log.warn("⚠️ Job [{}] not found in scheduler.", request.jobName());
                return false;
            }

            // Pause job first
            scheduler.pauseJob(jobKey);

            // Build new trigger
            TriggerKey triggerKey = new TriggerKey(request.jobName() + "Trigger", request.jobGroup());
            Trigger newTrigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .withSchedule(CronScheduleBuilder.cronSchedule(request.cronExpression()))
                    .build();

            // Reschedule
            scheduler.rescheduleJob(triggerKey, newTrigger);

            // Update DB
            schedulerRepository.findByJobNameAndJobGroup(request.jobName(), request.jobGroup())
                    .ifPresent(info -> {
                        info.setCronExpression(request.cronExpression());
                        info.setJobStatus("UPDATED");
                        info.setLastRunTime(LocalDateTime.now());
                        schedulerRepository.save(info);
                    });

            log.info("🔄 Job [{}] rescheduled with new cron [{}].",
                    request.jobName(), request.cronExpression());
            return true;

        } catch (SchedulerException e) {
            log.error("❌ Failed to update job [{}]", request.jobName(), e);
            return false;
        }
    }

    // ✅ Pause job and update DB status
    public boolean pauseJob(SchedulerJobInfo jobInfo) {
        try {
            Scheduler scheduler = schedulerFactoryBean.getScheduler();
            scheduler.pauseJob(new JobKey(jobInfo.getJobName(), jobInfo.getJobGroup()));

            schedulerRepository.findByJobNameAndJobGroup(jobInfo.getJobName(), jobInfo.getJobGroup())
                    .ifPresent(info -> {
                        jobInfo.setJobStatus("PAUSED");
                        schedulerRepository.save(jobInfo);
                    });

            log.info("⏸️ Job [{}] paused.", jobInfo.getJobName());
            return true;
        } catch (SchedulerException e) {
            log.error("Failed to pause job [{}]", jobInfo.getJobName(), e);
            return false;
        }
    }

    // ✅ Resume job and update DB status
    public boolean resumeJob(SchedulerJobInfo jobInfo) {
        try {
            Scheduler scheduler = schedulerFactoryBean.getScheduler();
            scheduler.resumeJob(new JobKey(jobInfo.getJobName(), jobInfo.getJobGroup()));

            schedulerRepository.findByJobNameAndJobGroup(jobInfo.getJobName(), jobInfo.getJobGroup())
                    .ifPresent(info -> {
                        jobInfo.setJobStatus("RESUMED");
                        schedulerRepository.save(jobInfo);
                    });

            log.info("▶️ Job [{}] resumed.", jobInfo.getJobName());
            return true;
        } catch (SchedulerException e) {
            log.error("Failed to resume job [{}]", jobInfo.getJobName(), e);
            return false;
        }
    }

    // ✅ Delete job and remove from DB
    public boolean deleteJob(SchedulerJobInfo jobInfo) {
        try {
            Scheduler scheduler = schedulerFactoryBean.getScheduler();
            scheduler.deleteJob(new JobKey(jobInfo.getJobName(), jobInfo.getJobGroup()));

            schedulerRepository.findByJobNameAndJobGroup(jobInfo.getJobName(), jobInfo.getJobGroup())
                    .ifPresent(info -> {
                        schedulerRepository.delete(jobInfo);
                    });
            log.info("🗑️ Job [{}] deleted.", jobInfo.getJobName());
            return true;
        } catch (SchedulerException e) {
            log.error("Failed to delete job [{}]", jobInfo.getJobName(), e);
            return false;
        }
    }

    // ✅ Meta for debug
    public SchedulerMetaData getMetaData() throws SchedulerException {
        return schedulerFactoryBean.getScheduler().getMetaData();
    }

    public List<SchedulerJobInfo> getAllJobList() {
        return schedulerRepository.findAll();
    }

    public boolean startJobNow(SchedulerJobInfo jobInfo) {
        try {
            schedulerFactoryBean.getScheduler().triggerJob(new JobKey(jobInfo.getJobName(), jobInfo.getJobGroup()));

            schedulerRepository.findByJobNameAndJobGroup(jobInfo.getJobName(), jobInfo.getJobGroup())
                    .ifPresent(info -> {
                        jobInfo.setJobStatus("SCHEDULED & STARTED");
                        schedulerRepository.save(jobInfo);
                    });
            log.info(">>>>> jobName = [{}] scheduled and started now.", jobInfo.getJobName());
            return true;
        } catch (SchedulerException e) {
            log.error("Failed to start new job - {}", jobInfo.getJobName(), e);
            return false;
        }
    }
}
