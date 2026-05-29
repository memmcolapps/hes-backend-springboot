package com.memmcol.hes.jobs;

import com.memmcol.hes.jobs.workers.ClassA_ReplayWorker;
import com.memmcol.hes.jobs.workers.ClassB_IncrementalWorker;
import com.memmcol.hes.schedulers.QuartzExecutionLogging;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

/**
 * Single Master Orchestrator for the nightly window (00:30 - 06:00).
 * Manages Class A (Data Correction/Replay) and Class B (Continuous Incremental) jobs.
 */
@Slf4j
@DisallowConcurrentExecution
@Component
public class NightlyMasterOrchestrator extends QuartzJobBean {

    @Autowired
    private ClassA_ReplayWorker classAWorker;

    @Autowired
    private ClassB_IncrementalWorker classBWorker;

    @Autowired
    private Scheduler scheduler;

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

        log.info("START: NightlyMasterOrchestrator [00:30 - 06:00 window]");
        QuartzExecutionLogging.logJobExecuteStart(log, context, "Nightly-Master");

        LocalTime cutoffTime = LocalTime.parse(windowEndStr);

        // --- Execute Class A: Data Correction / Replay ---
        if (classAEnabled) {
            log.info("Orchestrator: Starting Class A (Data Correction / Replay) Tasks");
            // Example OBIS codes for replay
            classAWorker.repairGaps("1.0.99.1.0.255", cutoffTime);
            classAWorker.repairGaps("1.0.99.2.0.255", cutoffTime);
        }

        // --- Execute Class B: Continuous Incremental ---
        if (classBEnabled) {
            if (LocalTime.now().isBefore(cutoffTime) || LocalTime.now().isAfter(LocalTime.of(22, 0))) {
                log.info("Orchestrator: Starting Class B (Continuous Incremental / Backlog) Tasks");
                classBWorker.processBacklog("0.1.24.3.0.255", cutoffTime);
                classBWorker.processBacklog("0.2.24.3.0.255", cutoffTime);
            } else {
                log.warn("Orchestrator: Skipping Class B - Window closed.");
            }
        }

        log.info("COMPLETED: NightlyMasterOrchestrator nightly run.");
    }
}
