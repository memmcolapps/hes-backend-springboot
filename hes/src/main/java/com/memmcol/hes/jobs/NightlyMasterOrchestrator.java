package com.memmcol.hes.jobs;

import com.memmcol.hes.model.SchedulerJobExecution;
import com.memmcol.hes.model.SchedulerJobInfo;
import com.memmcol.hes.repository.SchedulerJobExecutionRepository;
import com.memmcol.hes.repository.SchedulerRepository;
import com.memmcol.hes.schedulers.QuartzExecutionLogging;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.quartz.impl.triggers.SimpleTriggerImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Single Master Orchestrator for the nightly window (00:30 - 06:00).
 * Class A: Replays/Repairs jobs that failed in the last 24h.
 * Class B: Continues incremental processing for all Profile/Billing jobs.
 */
@Slf4j
@DisallowConcurrentExecution
@Component
public class NightlyMasterOrchestrator extends QuartzJobBean {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private SchedulerRepository schedulerRepository;

    @Autowired
    private SchedulerJobExecutionRepository jobExecutionRepository;

    @Value("${hes.nightly.orchestrator.enabled:true}")
    private boolean enabled;

    @Value("${hes.nightly.classA.enabled:true}")
    private boolean classAEnabled;

    @Value("${hes.nightly.classB.enabled:true}")
    private boolean classBEnabled;

    @Value("${hes.profile.execution.window.end:06:00}")
    private String windowEndStr;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        if (!enabled) {
            log.info("NightlyMasterOrchestrator is disabled in application.properties");
            return;
        }

        log.info("START: NightlyMasterOrchestrator nightly window [00:30 - 06:00]");
        QuartzExecutionLogging.logJobExecuteStart(log, context, "Nightly-Master-Maintenance");

        LocalTime cutoffTime = LocalTime.parse(windowEndStr);

        // --- Class A: Data Correction / Replay ---
        if (classAEnabled) {
            log.info("Orchestrator: Beginning Class A (Correction/Replay) cluster");
            replayFailedJobs(context, cutoffTime);
        }

        // --- Class B: Continuous Incremental ---
        if (classBEnabled) {
            log.info("Orchestrator: Beginning Class B (Continuous Incremental) cluster");
            processIncrementalBacklogs(context, cutoffTime);
        }

        log.info("COMPLETED: NightlyMasterOrchestrator nightly run.");
    }

    private void replayFailedJobs(JobExecutionContext context, LocalTime cutoff) {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        List<SchedulerJobExecution> failures = jobExecutionRepository.findByStatusAndStartedAtAfter("FAILED", since);
        failures.addAll(jobExecutionRepository.findByStatusAndStartedAtAfter("ERROR", since));

        List<String> failedJobNames = failures.stream()
                .map(SchedulerJobExecution::getJobName)
                .distinct()
                .collect(Collectors.toList());

        log.info("Class A: Identified {} unique failed jobs in last 24h: {}", failedJobNames.size(), failedJobNames);

        for (String jobName : failedJobNames) {
            if (isPastCutoff(cutoff)) break;

            schedulerRepository.findByJobNameAndJobGroup(jobName, null) // Group not needed if names are unique enough
                .or(() -> Optional.ofNullable(schedulerRepository.findByJobName(jobName)))
                .ifPresent(jobInfo -> {
                    try {
                        log.info("Class A: Replaying failed job -> {}", jobName);
                        executeSubordinateJob(jobInfo, context.getMergedJobDataMap());
                    } catch (Exception e) {
                        log.error("Class A: Replay failed for {}: {}", jobName, e.getMessage());
                    }
                });
        }
    }

    private void processIncrementalBacklogs(JobExecutionContext context, LocalTime cutoff) {
        List<SchedulerJobInfo> incrementalJobs = schedulerRepository.findAll().stream()
                .filter(j -> "profile".equalsIgnoreCase(j.getJobGroup()) || "billing".equalsIgnoreCase(j.getJobGroup()))
                .collect(Collectors.toList());

        log.info("Class B: Processing {} incremental/backlog jobs", incrementalJobs.size());

        for (SchedulerJobInfo jobInfo : incrementalJobs) {
            if (isPastCutoff(cutoff)) break;

            try {
                log.info("Class B: Incremental processing for -> {}", jobInfo.getJobName());
                executeSubordinateJob(jobInfo, context.getMergedJobDataMap());
            } catch (Exception e) {
                log.error("Class B: Incremental processing failed for {}: {}", jobInfo.getJobName(), e.getMessage());
            }
        }
    }

    private boolean isPastCutoff(LocalTime cutoff) {
        LocalTime now = LocalTime.now();
        if (now.isAfter(cutoff) && now.isBefore(LocalTime.of(22, 0))) {
            log.warn("Nightly window closed (passed {}). Gracefully terminating orchestrator task.", windowEndStr);
            return true;
        }
        return false;
    }

    private void executeSubordinateJob(SchedulerJobInfo jobInfo, JobDataMap parentDataMap) throws Exception {
        if (this.getClass().getName().equals(jobInfo.getJobClass())) return;

        Class<? extends Job> jobClass = (Class<? extends Job>) Class.forName(jobInfo.getJobClass());
        Job jobInstance = applicationContext.getBean(jobClass);

        JobDataMap childDataMap = new JobDataMap(parentDataMap);
        if (jobInfo.getObisCodes() != null) childDataMap.put("obisCodes", jobInfo.getObisCodes());
        if (jobInfo.getObisCodesHousehold() != null) childDataMap.put("obisCodesHousehold", jobInfo.getObisCodesHousehold());

        childDataMap.put("isNightlyOrchestrated", true);
        childDataMap.put("nightlyCutoff", windowEndStr);

        if (jobInstance instanceof QuartzJobBean) {
            jobInstance.execute(new JobExecutionContextImpl(jobInfo, childDataMap));
        }
    }

    private class JobExecutionContextImpl implements JobExecutionContext {
        private final SchedulerJobInfo jobInfo;
        private final JobDataMap jobDataMap;
        private final Trigger trigger;

        public JobExecutionContextImpl(SchedulerJobInfo jobInfo, JobDataMap childDataMap) {
            this.jobInfo = jobInfo;
            this.jobDataMap = childDataMap;
            SimpleTriggerImpl t = new SimpleTriggerImpl();
            t.setName(jobInfo.getJobName() + "_NightlyTrigger");
            t.setJobName(jobInfo.getJobName());
            t.setJobGroup(jobInfo.getJobGroup());
            this.trigger = t;
        }

        @Override public Scheduler getScheduler() { return scheduler; }
        @Override public Trigger getTrigger() { return trigger; }
        @Override public Calendar getCalendar() { return null; }
        @Override public boolean isRecovering() { return false; }
        @Override public TriggerKey getRecoveringTriggerKey() { return null; }
        @Override public int getRefireCount() { return 0; }
        @Override public JobDataMap getMergedJobDataMap() { return jobDataMap; }
        @Override public JobDetail getJobDetail() {
            return JobBuilder.newJob(Job.class).withIdentity(jobInfo.getJobName(), jobInfo.getJobGroup()).build();
        }
        @Override public Job getJobInstance() { return null; }
        @Override public java.util.Date getFireTime() { return new java.util.Date(); }
        @Override public java.util.Date getScheduledFireTime() { return new java.util.Date(); }
        @Override public java.util.Date getPreviousFireTime() { return null; }
        @Override public java.util.Date getNextFireTime() { return null; }
        @Override public String getFireInstanceId() { return "nightly-master-" + System.currentTimeMillis(); }
        @Override public Object getResult() { return null; }
        @Override public void setResult(Object result) {}
        @Override public long getJobRunTime() { return 0; }
        @Override public void put(Object key, Object value) {}
        @Override public Object get(Object key) { return null; }
    }
}
