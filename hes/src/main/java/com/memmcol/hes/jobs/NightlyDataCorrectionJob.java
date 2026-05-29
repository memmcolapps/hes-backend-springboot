package com.memmcol.hes.jobs;

import com.memmcol.hes.schedulers.QuartzExecutionLogging;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;

/**
 * Master orchestrator job that repairs missing or corrupted daytime data
 * during the nightly low-traffic window (00:30 - 06:00).
 */
@Slf4j
@DisallowConcurrentExecution
@Component
public class NightlyDataCorrectionJob extends QuartzJobBean {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private com.memmcol.hes.repository.SchedulerRepository schedulerRepository;

    @Value("${hes.nightly.correction.enabled:true}")
    private boolean enabled;

    @Value("${hes.profile.execution.window.end:06:00}")
    private String windowEndStr;

    private static final List<String> SUBORDINATE_JOBS = List.of(
            "Channel1Job",
            "Channel2Job",
            "Channel1JobHouseHold",
            "Channel2JobHouseHold",
            "DailyBillingDataHouseHoldJob",
            "DailyBillingEnergyHouseHoldJob"
    );

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        if (!enabled) {
            log.info("NightlyDataCorrectionJob is disabled in application.properties");
            return;
        }

        log.info("START: NightlyDataCorrectionJob orchestrator");
        QuartzExecutionLogging.logJobExecuteStart(log, context, "Nightly-Orchestrator");

        LocalTime cutoffTime = LocalTime.parse(windowEndStr);

        for (String jobName : SUBORDINATE_JOBS) {
            if (LocalTime.now().isAfter(cutoffTime) && LocalTime.now().isBefore(LocalTime.of(22, 0))) {
                log.warn("Nightly window closed (passed {}). Gracefully terminating orchestrator.", windowEndStr);
                break;
            }

            try {
                log.info("Orchestrator: Triggering subordinate job -> {}", jobName);
                executeSubordinateJob(jobName, context.getMergedJobDataMap());
            } catch (Exception e) {
                log.error("Orchestrator: Failed to execute subordinate job {}: {}", jobName, e.getMessage());
            }
        }

        log.info("COMPLETED: NightlyDataCorrectionJob orchestrator");
    }

    private void executeSubordinateJob(String jobClassName, JobDataMap parentDataMap) throws Exception {
        // We find the job class by name in the package
        Class<? extends Job> jobClass = (Class<? extends Job>) Class.forName("com.memmcol.hes.jobs." + jobClassName);

        Job jobInstance = applicationContext.getBean(jobClass);

        // Fetch job info from DB to get the correct OBIS codes
        com.memmcol.hes.model.SchedulerJobInfo jobInfo = schedulerRepository.findByJobName(jobClassName);
        JobDataMap childDataMap = new JobDataMap(parentDataMap);
        if (jobInfo != null) {
            if (jobInfo.getObisCodes() != null) {
                childDataMap.put("obisCodes", jobInfo.getObisCodes());
            }
            if (jobInfo.getObisCodesHousehold() != null) {
                childDataMap.put("obisCodesHousehold", jobInfo.getObisCodesHousehold());
            }
        }

        if (jobInstance instanceof QuartzJobBean) {
            log.info("Orchestrator: Executing {} with OBIS={}", jobClassName, childDataMap.getString("obisCodes"));
            jobInstance.execute(new JobExecutionContextImpl(jobClassName, childDataMap));
        }
    }

    /**
     * Minimal implementation of JobExecutionContext to allow manual job invocation.
     */
    private class JobExecutionContextImpl implements JobExecutionContext {
        private final String jobName;
        private final JobDataMap jobDataMap;

        public JobExecutionContextImpl(String jobName, JobDataMap parentDataMap) {
            this.jobName = jobName;
            this.jobDataMap = new JobDataMap(parentDataMap);
            // Ensure child job gets its specific OBIS if defined in DB/default-jobs,
            // though here we rely on what's passed or defaults.
        }

        @Override public Scheduler getScheduler() { return scheduler; }
        @Override public Trigger getTrigger() { return null; }
        @Override public Calendar getCalendar() { return null; }
        @Override public boolean isRecovering() { return false; }
        @Override public TriggerKey getRecoveringTriggerKey() throws IllegalStateException { return null; }
        @Override public int getRefireCount() { return 0; }
        @Override public JobDataMap getMergedJobDataMap() { return jobDataMap; }
        @Override public JobDetail getJobDetail() {
            return JobBuilder.newJob(Job.class).withIdentity(jobName).build();
        }
        @Override public Job getJobInstance() { return null; }
        @Override public java.util.Date getFireTime() { return new java.util.Date(); }
        @Override public java.util.Date getScheduledFireTime() { return new java.util.Date(); }
        @Override public java.util.Date getPreviousFireTime() { return null; }
        @Override public java.util.Date getNextFireTime() { return null; }
        @Override public String getFireInstanceId() { return "nightly-orchestrator-" + System.currentTimeMillis(); }
        @Override public Object getResult() { return null; }
        @Override public void setResult(Object result) {}
        @Override public long getJobRunTime() { return 0; }
        @Override public void put(Object key, Object value) {}
        @Override public Object get(Object key) { return null; }
    }
}
