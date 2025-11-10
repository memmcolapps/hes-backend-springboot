package com.memmcol.hes.schedulers;

import com.memmcol.hes.component.JobScheduleCreator;
import com.memmcol.hes.jobs.SampleCronJob;
import com.memmcol.hes.jobs.SimpleJob;
import com.memmcol.hes.model.SchedulerJobInfo;
import com.memmcol.hes.repository.SchedulerRepository;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@Transactional
public class QuartzJobService {
    private final Scheduler scheduler;
    private final SchedulerRepository schedulerRepository;

    @Autowired
    public QuartzJobService(
            Scheduler scheduler,
            SchedulerRepository schedulerRepository
    ) {
        this.scheduler = scheduler;
        this.schedulerRepository = schedulerRepository;
    }

    // ---------------- META ----------------
    public SchedulerMetaData getMetaData() throws SchedulerException {
        return scheduler.getMetaData();
    }

    public List<SchedulerJobInfo> getAllJobsFromDb() {
        return schedulerRepository.findAll();
    }

    public Map<String, String> getAllJobsFromQuartz() throws SchedulerException {
        Map<String, String> result = new HashMap<>();
        for (String groupName : scheduler.getJobGroupNames()) {
            for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName))) {
                result.put(jobKey.toString(), getJobStatus(jobKey.getName(), jobKey.getGroup()));
            }
        }
        return result;
    }

    // ---------------- STATUS ----------------
    public String getJobStatus(String jobName, String jobGroup) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
        if (!scheduler.checkExists(jobKey)) return "NOT_FOUND";

        List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
        if (triggers == null || triggers.isEmpty()) return "NO_TRIGGER";

        Trigger.TriggerState state = scheduler.getTriggerState(triggers.get(0).getKey());
        return switch (state) {
            case NONE -> "NONE";
            case NORMAL -> "RUNNING";
            case PAUSED -> "PAUSED";
            case COMPLETE -> "COMPLETED";
            case ERROR -> "ERROR";
            case BLOCKED -> "BLOCKED";
        };
    }



    // ---------------- CRUD ----------------


    /**
     * Save a job only if it does NOT exist.
     * If it exists, do nothing.
     */
    public void saveIfNew(SchedulerJobInfo jobInfo) throws Exception {
        boolean exists = schedulerRepository
                .findByJobNameAndJobGroup(jobInfo.getJobName(), jobInfo.getJobGroup())
                .isPresent();

        if (exists) {
            log.info("Job [{}] already exists. Skipping insert.", jobInfo.getJobName());
            return; // Ignore existing jobs
        }

        // Validation
        if (jobInfo.getJobClass() == null || jobInfo.getJobClass().isBlank()) {
            throw new IllegalArgumentException("jobClass is required and cannot be empty.");
        }

        // Decide if it's a cron job
        jobInfo.setCronJob(StringUtils.hasText(jobInfo.getCronExpression()));

        scheduleNewJob(jobInfo); // schedule in Quartz
        schedulerRepository.save(jobInfo);
        log.info("✅ New job [{}] inserted successfully.", jobInfo.getJobName());
    }
    /**
     * Update a job only if it exists.
     * If the job does not exist, do nothing.
     */
    public void updateIfExists(SchedulerJobInfo jobInfo) throws Exception {
        SchedulerJobInfo existingJob = schedulerRepository
                .findByJobNameAndJobGroup(jobInfo.getJobName(), jobInfo.getJobGroup())
                .orElse(null);

        if (existingJob == null) {
            log.info("Job [{}] does not exist. Skipping update.", jobInfo.getJobName());
            return; // Ignore new jobs
        }

        // Merge only provided fields
        mergeJobFields(existingJob, jobInfo);

        updateJob(existingJob); // update Quartz schedule if needed
        schedulerRepository.save(existingJob);

        log.info("✅ Existing job [{}] updated successfully.", existingJob.getJobName());
    }

    /**
     * Create or update a job.
     * Supports partial updates safely.
     */
    public void saveOrUpdate(SchedulerJobInfo jobInfo) throws Exception {
        SchedulerJobInfo existingJob = schedulerRepository
                .findByJobNameAndJobGroup(jobInfo.getJobName(), jobInfo.getJobGroup())
                .orElse(null);

        if (existingJob != null) {
            // 🔄 Partial update: merge only provided fields
            mergeJobFields(existingJob, jobInfo);
            updateJob(existingJob);
            schedulerRepository.save(existingJob);
            log.info("✅ Job [{}] updated successfully (partial update supported).", existingJob.getJobName());
        } else {
            // ✅ Validation
            if (jobInfo.getJobClass() == null || jobInfo.getJobClass().isBlank()) {
                throw new IllegalArgumentException("jobClass is required and cannot be empty.");
            }

            // 🆕 New job creation
            if (!StringUtils.hasText(jobInfo.getJobClass())) {
                throw new IllegalArgumentException("❌ jobClass must be provided for new jobs.");
            }

            // Decide if it's a cron job
            jobInfo.setCronJob(StringUtils.hasText(jobInfo.getCronExpression()));

            scheduleNewJob(jobInfo);
            schedulerRepository.save(jobInfo);
            log.info("✅ New job [{}] created successfully with jobClass={}",
                    jobInfo.getJobName(), jobInfo.getJobClass());
        }
    }

    @SuppressWarnings("unchecked")
    public void scheduleNewJob(SchedulerJobInfo jobInfo) {
        try {
            Class<? extends QuartzJobBean> jobClazz =
                    (Class<? extends QuartzJobBean>) Class.forName(jobInfo.getJobClass());

            JobDataMap jobDataMap = new JobDataMap();
            jobDataMap.put("description", jobInfo.getDescription());
            jobDataMap.put("interfaceName", jobInfo.getInterfaceName());
            // ✅ Add OBIS codes (single or multiple)
            if (jobInfo.getObisCodes() != null && !jobInfo.getObisCodes().isEmpty()) {
                jobDataMap.put("obisCodes", jobInfo.getObisCodes());
            }

            JobDetail jobDetail = JobBuilder.newJob(jobClazz)
                    .withIdentity(jobInfo.getJobName(), jobInfo.getJobGroup())
                    .storeDurably()
                    .usingJobData(jobDataMap)
                    .build();

            Trigger trigger = jobInfo.getCronJob()
                    ? TriggerBuilder.newTrigger()
                    .forJob(jobDetail)
                    .withIdentity(jobInfo.getJobName(), jobInfo.getJobGroup())
                    .usingJobData(jobDetail.getJobDataMap())
                    .withSchedule(CronScheduleBuilder.cronSchedule(jobInfo.getCronExpression())
                            .withMisfireHandlingInstructionFireAndProceed())
                    .build()
                    : TriggerBuilder.newTrigger()
                    .forJob(jobDetail)
                    .withIdentity(jobInfo.getJobName(), jobInfo.getJobGroup())
                    .startNow()
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withIntervalInMilliseconds(jobInfo.getRepeatTime())
                            .repeatForever()
                            .withMisfireHandlingInstructionFireNow())
                    .build();

            if (!scheduler.checkExists(jobDetail.getKey())) {
                scheduler.scheduleJob(jobDetail, trigger);
                jobInfo.setJobStatus("SCHEDULED");
                schedulerRepository.save(jobInfo);
                log.info("✅ Job [{}] scheduled (class={}, desc={}, interface={}, obis={})",
                        jobInfo.getJobName(), jobInfo.getJobClass(),
                        jobInfo.getDescription(), jobInfo.getInterfaceName(),
                        jobInfo.getObisCodes());
            } else {
                log.warn("⚠️ Job [{}] already exists. Skipping.", jobInfo.getJobName());
            }

        } catch (ClassNotFoundException e) {
            log.error("❌ Class not found - {}", jobInfo.getJobClass(), e);
        } catch (SchedulerException e) {
            log.error("❌ Scheduler exception for job [{}]: {}", jobInfo.getJobName(), e.getMessage(), e);
        }
    }

    /**
     * Update an existing job/trigger in Quartz
     */
    public void updateJob(SchedulerJobInfo jobInfo) {
        try {
            JobKey jobKey = JobKey.jobKey(jobInfo.getJobName(), jobInfo.getJobGroup());
            TriggerKey triggerKey = TriggerKey.triggerKey(jobInfo.getJobName(), jobInfo.getJobGroup());

            Class<? extends QuartzJobBean> jobClazz =
                    (Class<? extends QuartzJobBean>) Class.forName(jobInfo.getJobClass());

            JobDataMap jobDataMap = new JobDataMap();
            jobDataMap.put("description", jobInfo.getDescription());
            jobDataMap.put("interfaceName", jobInfo.getInterfaceName());

            JobDetail newJobDetail = JobBuilder.newJob(jobClazz)
                    .withIdentity(jobKey)
                    .storeDurably()
                    .usingJobData(jobDataMap)
                    .build();

            Trigger newTrigger = buildTrigger(jobInfo, newJobDetail);

            if (scheduler.checkExists(jobKey)) {
                scheduler.addJob(newJobDetail, true); // replace existing job
                scheduler.rescheduleJob(triggerKey, newTrigger);
            } else {
                scheduler.scheduleJob(newJobDetail, newTrigger);
            }

            jobInfo.setJobStatus("UPDATED");
            schedulerRepository.save(jobInfo);

        } catch (ClassNotFoundException | SchedulerException e) {
            log.error("Failed to update job [{}]: {}", jobInfo.getJobName(), e.getMessage(), e);
        }
    }

    public boolean deleteJob(String jobName, String jobGroup) {
        try {
            SchedulerJobInfo dbJob = schedulerRepository.findByJobName(jobName);
            if (dbJob != null) schedulerRepository.delete(dbJob);

            boolean deleted = scheduler.deleteJob(new JobKey(jobName, jobGroup));
            log.info("🗑️ Job [{}] deleted: {}", jobName, deleted);
            return deleted;
        } catch (SchedulerException e) {
            log.error("❌ Failed to delete job [{}]: {}", jobName, e.getMessage(), e);
            return false;
        }
    }

    // ---------------- CONTROL ----------------
    public boolean pauseJob(String jobName, String jobGroup) {
        try {
            scheduler.pauseJob(new JobKey(jobName, jobGroup));
            SchedulerJobInfo dbJob = schedulerRepository.findByJobName(jobName);
            if (dbJob != null) {
                dbJob.setJobStatus("PAUSED");
                schedulerRepository.save(dbJob);
            }
            log.info("⏸️ Job [{}] paused.", jobName);
            return true;
        } catch (SchedulerException e) {
            log.error("❌ Failed to pause job [{}]: {}", jobName, e.getMessage(), e);
            return false;
        }
    }

    public boolean resumeJob(String jobName, String jobGroup) {
        try {
            scheduler.resumeJob(new JobKey(jobName, jobGroup));
            SchedulerJobInfo dbJob = schedulerRepository.findByJobName(jobName);
            if (dbJob != null) {
                dbJob.setJobStatus("RESUMED");
                schedulerRepository.save(dbJob);
            }
            log.info("▶️ Job [{}] resumed.", jobName);
            return true;
        } catch (SchedulerException e) {
            log.error("❌ Failed to resume job [{}]: {}", jobName, e.getMessage(), e);
            return false;
        }
    }

    public boolean triggerNow(String jobName, String jobGroup) {
        try {
            scheduler.triggerJob(new JobKey(jobName, jobGroup));
            SchedulerJobInfo dbJob = schedulerRepository.findByJobName(jobName);
            if (dbJob != null) {
                dbJob.setJobStatus("TRIGGERED_NOW");
                schedulerRepository.save(dbJob);
            }
            log.info("⚡ Job [{}] triggered now.", jobName);
            return true;
        } catch (SchedulerException e) {
            log.error("❌ Failed to trigger job [{}]: {}", jobName, e.getMessage(), e);
            return false;
        }
    }

    // ---------------- HELPER METHODS ----------------

    /**
     * Merge non-null fields from incoming jobInfo into existing job
     */
    private void mergeJobFields(SchedulerJobInfo existingJob, SchedulerJobInfo incoming) {
        if (incoming.getDescription() != null) existingJob.setDescription(incoming.getDescription());
        if (incoming.getInterfaceName() != null) existingJob.setInterfaceName(incoming.getInterfaceName());
        if (incoming.getJobClass() != null) existingJob.setJobClass(incoming.getJobClass());
        if (incoming.getCronExpression() != null) existingJob.setCronExpression(incoming.getCronExpression());

        if (incoming.getRepeatTime() != null) existingJob.setRepeatTime(incoming.getRepeatTime());
        if (incoming.getRepeatSeconds() != null) existingJob.setRepeatSeconds(incoming.getRepeatSeconds());
        if (incoming.getRepeatMinutes() != null) existingJob.setRepeatMinutes(incoming.getRepeatMinutes());
        if (incoming.getRepeatHours() != null) existingJob.setRepeatHours(incoming.getRepeatHours());

        existingJob.setCronJob(incoming.getCronJob() != null ? incoming.getCronJob()
                : StringUtils.hasText(existingJob.getCronExpression()));
    }

    /**
     * Calculate interval in milliseconds from hours/minutes/seconds or repeatTime
     */
    private long calculateIntervalMs(SchedulerJobInfo jobInfo) {
        if (jobInfo.getRepeatTime() != null && jobInfo.getRepeatTime() > 0) return jobInfo.getRepeatTime();
        long ms = 0;
        ms += (jobInfo.getRepeatHours() != null ? jobInfo.getRepeatHours() * 3600L : 0);
        ms += (jobInfo.getRepeatMinutes() != null ? jobInfo.getRepeatMinutes() * 60L : 0);
        ms += (jobInfo.getRepeatSeconds() != null ? jobInfo.getRepeatSeconds() : 0);
        ms *= 1000;
        return ms > 0 ? ms : 1000L; // default 1 sec
    }

    /**
     * Helper to build a trigger (cron or interval)
     */
    private Trigger buildTrigger(SchedulerJobInfo jobInfo, JobDetail jobDetail) {
        if (Boolean.TRUE.equals(jobInfo.getCronJob()) && StringUtils.hasText(jobInfo.getCronExpression())) {
            return TriggerBuilder.newTrigger()
                    .forJob(jobDetail)
                    .withIdentity(jobInfo.getJobName(), jobInfo.getJobGroup())
                    .withSchedule(CronScheduleBuilder.cronSchedule(jobInfo.getCronExpression())
                            .withMisfireHandlingInstructionFireAndProceed())
                    .build();
        } else {
            long intervalMs = calculateIntervalMs(jobInfo);
            return TriggerBuilder.newTrigger()
                    .forJob(jobDetail)
                    .withIdentity(jobInfo.getJobName(), jobInfo.getJobGroup())
                    .startNow()
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withIntervalInMilliseconds(intervalMs)
                            .repeatForever()
                            .withMisfireHandlingInstructionFireNow())
                    .build();
        }
    }

    // ---------- Helper: find existing TriggerKey (if any) ----------
    private Optional<TriggerKey> findExistingTriggerKey(Scheduler scheduler, JobKey jobKey) throws SchedulerException {
        List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
        if (triggers != null && !triggers.isEmpty()) {
            // pick first trigger (common case)
            return Optional.of(triggers.getFirst().getKey());
        }
        // fallback common naming pattern
        return Optional.of(TriggerKey.triggerKey(jobKey.getName() + "Trigger", jobKey.getGroup()));
    }

    // ---------- Update interval (milliseconds) ----------
    public boolean updateJobInterval(String jobName, String jobGroup, long intervalMs) {
        try {
            JobKey jobKey = JobKey.jobKey(jobName, jobGroup);

            if (!scheduler.checkExists(jobKey)) {
                log.warn("Job not found in scheduler: {}/{}", jobGroup, jobName);
                return false;
            }

            // find existing triggerKey if present
            Optional<TriggerKey> optTriggerKey = findExistingTriggerKey(scheduler, jobKey);

            TriggerKey triggerKey = optTriggerKey.orElse(TriggerKey.triggerKey(jobName + "Trigger", jobGroup));

            Trigger newTrigger = TriggerBuilder.newTrigger()
                    .forJob(jobKey)
                    .withIdentity(triggerKey)
                    .startNow()
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withIntervalInMilliseconds(intervalMs)
                            .repeatForever()
                            .withMisfireHandlingInstructionFireNow())
                    .build();

            // If trigger actually existed in Quartz, reschedule; otherwise schedule new trigger (job exists)
            if (scheduler.checkExists(triggerKey)) {
                scheduler.rescheduleJob(triggerKey, newTrigger);
            } else {
                // scheduleJob(Trigger) works only if job exists; it will create a new trigger for existing job
                scheduler.scheduleJob(newTrigger);
            }

            // persist changes to scheduler_job_info AFTER scheduler operation succeeds
            schedulerRepository.findByJobNameAndJobGroup(jobName, jobGroup)
                    .ifPresent(info -> {
                        info.setRepeatTime(intervalMs);
                        // optional: store fields breakdown
                        info.setRepeatSeconds((int) (intervalMs / 1000L));
                        info.setRepeatMinutes((int) (intervalMs / 1000L / 60L));
                        info.setRepeatHours((int) (intervalMs / 1000L / 3600L));
                        info.setCronJob(false);
                        info.setCronExpression("");
                        info.setJobStatus("UPDATED");
                        info.setLastRunTime(LocalDateTime.now());
                        schedulerRepository.save(info);
                    });

            log.info("Updated interval for job {}/{} -> {} ms", jobGroup, jobName, intervalMs);
            return true;
        } catch (Exception e) {
            log.error("Failed to update interval for job {}/{}", jobGroup, jobName, e);
            return false;
        }
    }

    // ---------- Update using Cron expression ----------
    public boolean updateJobCron(String jobName, String jobGroup, String cronExpression) {
        try {
            JobKey jobKey = JobKey.jobKey(jobName, jobGroup);

            if (!scheduler.checkExists(jobKey)) {
                log.warn("Job not found in scheduler: {}/{}", jobGroup, jobName);
                return false;
            }

            // find existing triggerKey
            Optional<TriggerKey> optTriggerKey = findExistingTriggerKey(scheduler, jobKey);
            TriggerKey triggerKey = optTriggerKey.orElse(TriggerKey.triggerKey(jobName + "Trigger", jobGroup));

            Trigger newTrigger = TriggerBuilder.newTrigger()
                    .forJob(jobKey)
                    .withIdentity(triggerKey)
                    .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression)
                            .withMisfireHandlingInstructionDoNothing())
                    .build();

            if (scheduler.checkExists(triggerKey)) {
                scheduler.rescheduleJob(triggerKey, newTrigger);
            } else {
                scheduler.scheduleJob(newTrigger);
            }

            // persist update AFTER successful reschedule
            schedulerRepository.findByJobNameAndJobGroup(jobName, jobGroup)
                    .ifPresent(info -> {
                        info.setCronExpression(cronExpression);
                        info.setCronJob(true);
                        info.setRepeatTime(0L);
                        info.setRepeatMinutes(0);
                        info.setRepeatHours(0);
                        info.setRepeatSeconds(0);
                        info.setJobStatus("UPDATED");
                        info.setLastRunTime(LocalDateTime.now());
                        schedulerRepository.save(info);
                    });

            log.info("Updated CRON for job {}/{} -> {}", jobGroup, jobName, cronExpression);
            return true;
        } catch (Exception e) {
            log.error("Failed to update CRON for job {}/{}", jobGroup, jobName, e);
            return false;
        }
    }

    // Convenience wrappers for seconds/minutes/hours
    public boolean updateJobIntervalSeconds(String jobName, String jobGroup, int seconds) {
        long ms = Math.max(1000L, seconds * 1000L);
        return updateJobInterval(jobName, jobGroup, ms);
    }

    public boolean updateJobIntervalMinutes(String jobName, String jobGroup, int minutes) {
        long ms = Math.max(60000L, minutes * 60L * 1000L);
        return updateJobInterval(jobName, jobGroup, ms);
    }

    public boolean updateJobIntervalHours(String jobName, String jobGroup, int hours) {
        long ms = Math.max(3600L * 1000L, hours * 3600L * 1000L);
        return updateJobInterval(jobName, jobGroup, ms);
    }


    public boolean updateJobObisCodes(String jobName, String jobGroup, String obisCodes) {
        try {
            JobKey jobKey = JobKey.jobKey(jobName, jobGroup);

            if (!scheduler.checkExists(jobKey)) {
                log.warn("Job not found in scheduler: {}/{}", jobGroup, jobName);
                return false;
            }

            // ✅ Fetch current JobDetail
            JobDetail jobDetail = scheduler.getJobDetail(jobKey);
            JobDataMap jobDataMap = jobDetail.getJobDataMap();

            // ✅ Validate OBIS codes input
            if (obisCodes == null || obisCodes.trim().isEmpty()) {
                log.warn("No OBIS codes provided for update on job {}/{}", jobGroup, jobName);
                return false;
            }

            // ✅ Update obisCodes in JobDataMap
            jobDataMap.put("obisCodes", obisCodes);

            // ✅ Rebuild JobDetail with durability to avoid "Jobs added with no trigger" error
            JobDetail newJobDetail = jobDetail.getJobBuilder()
                    .usingJobData(jobDataMap)
                    .storeDurably(true) // ✅ mark as durable so it can exist without a trigger
                    .build();

            // ✅ Replace existing job definition in the scheduler
            scheduler.addJob(newJobDetail, true);

            // 🔄 Handle trigger rescheduling if trigger already exists
            Optional<TriggerKey> optTriggerKey = findExistingTriggerKey(scheduler, jobKey);
            if (optTriggerKey.isPresent()) {
                TriggerKey triggerKey = optTriggerKey.get();
                Trigger oldTrigger = scheduler.getTrigger(triggerKey);

                if (oldTrigger != null) {
                    Trigger newTrigger = oldTrigger.getTriggerBuilder()
                            .usingJobData(jobDataMap) // propagate updated OBIS codes into trigger
                            .build();

                    scheduler.rescheduleJob(triggerKey, newTrigger);
                    log.info("Rescheduled trigger for job {}/{} with new OBIS codes", jobGroup, jobName);
                }
            } else {
                log.info("No existing trigger found for job {}/{} — only job data updated", jobGroup, jobName);
            }

            // ✅ Persist OBIS update to DB only after successful scheduler update
            schedulerRepository.findByJobNameAndJobGroup(jobName, jobGroup)
                    .ifPresent(info -> {
                        info.setObisCodes(obisCodes);
                        info.setJobStatus("UPDATED_OBIS");
                        info.setLastRunTime(LocalDateTime.now());
                        schedulerRepository.save(info);
                    });

            log.info("✅ Successfully updated OBIS codes for job {}/{} -> {}", jobGroup, jobName, obisCodes);
            return true;

        } catch (Exception e) {
            log.error("❌ Failed to update OBIS codes for job {}/{}", jobGroup, jobName, e);
            return false;
        }
    }
}
