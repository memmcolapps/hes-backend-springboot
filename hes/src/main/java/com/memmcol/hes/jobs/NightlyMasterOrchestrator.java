package com.memmcol.hes.jobs;

import com.memmcol.hes.model.SchedulerJobInfo;
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

import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Single Master Orchestrator for the nightly window (00:30 - 06:00).
 * Dynamically runs all subordinate jobs from the database, categorized into:
 * Class A: Data Correction / Replay (e.g., Event jobs)
 * Class B: Continuous Incremental (e.g., Profile and Billing jobs)
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
        QuartzExecutionLogging.logJobExecuteStart(log, context, "Nightly-Master-Dynamic");

        LocalTime cutoffTime = LocalTime.parse(windowEndStr);

        // Fetch all jobs defined in the system
        List<SchedulerJobInfo> allJobs = schedulerRepository.findAll();

        // Class A: Correction / Replay (Event jobs tend to be about capturing missing point-in-time data)
        if (classAEnabled) {
            List<SchedulerJobInfo> classA = allJobs.stream()
                    .filter(j -> "event".equalsIgnoreCase(j.getJobGroup()))
                    .collect(Collectors.toList());
            log.info("Orchestrator: Processing Class A (Correction) - {} jobs", classA.size());
            runCluster(classA, context, cutoffTime);
        }

        // Class B: Incremental (Profile and Billing jobs are stateful and process backlogs)
        if (classBEnabled) {
            List<SchedulerJobInfo> classB = allJobs.stream()
                    .filter(j -> "profile".equalsIgnoreCase(j.getJobGroup()) || "billing".equalsIgnoreCase(j.getJobGroup()))
                    .collect(Collectors.toList());
            log.info("Orchestrator: Processing Class B (Incremental) - {} jobs", classB.size());
            runCluster(classB, context, cutoffTime);
        }

        log.info("COMPLETED: NightlyMasterOrchestrator dynamic nightly run.");
    }

    private void runCluster(List<SchedulerJobInfo> cluster, JobExecutionContext context, LocalTime cutoff) {
        for (SchedulerJobInfo jobInfo : cluster) {
            if (this.getClass().getName().equals(jobInfo.getJobClass())) continue;

            if (isPastCutoff(cutoff)) {
                log.warn("Nightly window closed (passed {}). Gracefully terminating orchestrator cluster.", windowEndStr);
                return;
            }

            try {
                log.info("Orchestrator: Triggering -> {}", jobInfo.getJobName());
                executeSubordinateJob(jobInfo, context.getMergedJobDataMap());
            } catch (Exception e) {
                log.error("Orchestrator: Failed to execute job {}: {}", jobInfo.getJobName(), e.getMessage());
            }
        }
    }

    private boolean isPastCutoff(LocalTime cutoff) {
        LocalTime now = LocalTime.now();
        return now.isAfter(cutoff) && now.isBefore(LocalTime.of(22, 0));
    }

    private void executeSubordinateJob(SchedulerJobInfo jobInfo, JobDataMap parentDataMap) throws Exception {
        Class<? extends Job> jobClass = (Class<? extends Job>) Class.forName(jobInfo.getJobClass());
        Job jobInstance = applicationContext.getBean(jobClass);

        JobDataMap childDataMap = new JobDataMap(parentDataMap);
        if (jobInfo.getObisCodes() != null) childDataMap.put("obisCodes", jobInfo.getObisCodes());
        if (jobInfo.getObisCodesHousehold() != null) childDataMap.put("obisCodesHousehold", jobInfo.getObisCodesHousehold());

        childDataMap.put("isNightlyOrchestrated", true);
        childDataMap.put("nightlyCutoff", windowEndStr);

        if (jobInstance instanceof QuartzJobBean) {
            log.info("Orchestrator: Sequential execution of {} [OBIS={}]", jobInfo.getJobName(), childDataMap.getString("obisCodes"));
            jobInstance.execute(new JobExecutionContextImpl(jobInfo, childDataMap));
        }
    }

    /**
     * Enhanced implementation of JobExecutionContext to provide safe stubs for subordinate jobs.
     */
    private class JobExecutionContextImpl implements JobExecutionContext {
        private final SchedulerJobInfo jobInfo;
        private final JobDataMap jobDataMap;
        private final Trigger trigger;

        public JobExecutionContextImpl(SchedulerJobInfo jobInfo, JobDataMap childDataMap) {
            this.jobInfo = jobInfo;
            this.jobDataMap = childDataMap;
            // Create a simple dummy trigger to avoid NPE in jobs that check trigger state
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
            return JobBuilder.newJob(Job.class)
                    .withIdentity(jobInfo.getJobName(), jobInfo.getJobGroup())
                    .withDescription(jobInfo.getDescription())
                    .build();
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
