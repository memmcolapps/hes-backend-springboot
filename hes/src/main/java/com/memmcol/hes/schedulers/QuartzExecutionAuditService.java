package com.memmcol.hes.schedulers;

import com.memmcol.hes.model.SchedulerJobExecution;
import com.memmcol.hes.model.SchedulerJobInfo;
import com.memmcol.hes.repository.SchedulerJobExecutionRepository;
import com.memmcol.hes.repository.SchedulerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;

/**
 * Persists every trigger lifecycle to {@code scheduler_job_execution} and updates
 * {@code scheduler_job_info} when a catalog row exists. Uses REQUIRES_NEW so failures
 * never roll back the Quartz worker transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuartzExecutionAuditService {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_VETOED = "VETOED";

    private static final String TBL_EXECUTION = "scheduler_job_execution";
    private static final String TBL_CATALOG = "scheduler_job_info";

    private static final int ERROR_MSG_MAX = 8000;

    private final SchedulerJobExecutionRepository executionRepository;
    private final SchedulerRepository schedulerRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onJobToBeExecuted(JobExecutionContext context) {
        String fireInstanceId = context.getFireInstanceId();
        String jobName = context.getJobDetail().getKey().getName();
        String jobGroup = context.getJobDetail().getKey().getGroup();
        LocalDateTime now = LocalDateTime.now();
        String schedulerInstanceId = safeSchedulerInstanceId(context);

        try {
            SchedulerJobExecution row = new SchedulerJobExecution();
            row.setFireInstanceId(fireInstanceId);
            row.setJobName(jobName);
            row.setJobGroup(jobGroup);
            row.setScheduledFireTime(toLocalDateTime(context.getScheduledFireTime()));
            row.setFireTime(toLocalDateTime(context.getFireTime()));
            row.setStartedAt(now);
            row.setStatus(STATUS_RUNNING);
            row.setSchedulerInstanceId(schedulerInstanceId);
            executionRepository.save(row);
            log.info(
                    "Successfully saved {} record(s) to database (table={}, operation=INSERT, purpose=quartz execution audit, listenerPhase=jobToBeExecuted, status={}). job={} jobGroup={} fireInstanceId={} hes.quartz.listener phase=LISTENER_START_PERSISTED",
                    1, TBL_EXECUTION, STATUS_RUNNING, jobName, jobGroup, fireInstanceId);
        } catch (Exception e) {
            log.error(
                    "Error saving record(s) to database (table={}, operation=INSERT, purpose=quartz execution audit, listenerPhase=jobToBeExecuted). recordsSaved=0. job={} jobGroup={} fireInstanceId={}. cause={}",
                    TBL_EXECUTION, jobName, jobGroup, fireInstanceId, truncateMessage(e.getMessage()), e);
        }

        touchCatalogRow(context, STATUS_RUNNING, now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onJobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        String fireInstanceId = context.getFireInstanceId();
        LocalDateTime now = LocalDateTime.now();
        boolean failed = jobException != null;
        String terminalStatus = failed ? STATUS_FAILED : STATUS_COMPLETED;
        String errorMessage = failed ? buildErrorMessage(jobException) : null;
        String jobName = context.getJobDetail().getKey().getName();
        String jobGroup = context.getJobDetail().getKey().getGroup();

        try {
            Optional<SchedulerJobExecution> existing = executionRepository.findByFireInstanceId(fireInstanceId);
            if (existing.isPresent()) {
                SchedulerJobExecution row = existing.get();
                row.setEndedAt(now);
                row.setStatus(terminalStatus);
                row.setErrorMessage(errorMessage);
                executionRepository.save(row);
                log.info(
                        "Successfully saved {} record(s) to database (table={}, operation=UPDATE, purpose=quartz execution audit, listenerPhase=jobWasExecuted, status={}). job={} jobGroup={} fireInstanceId={} hes.quartz.listener phase=LISTENER_END_PERSISTED",
                        1, TBL_EXECUTION, terminalStatus, jobName, jobGroup, fireInstanceId);
            } else {
                SchedulerJobExecution row = new SchedulerJobExecution();
                row.setFireInstanceId(fireInstanceId);
                row.setJobName(jobName);
                row.setJobGroup(jobGroup);
                row.setScheduledFireTime(toLocalDateTime(context.getScheduledFireTime()));
                row.setFireTime(toLocalDateTime(context.getFireTime()));
                row.setStartedAt(now);
                row.setEndedAt(now);
                row.setStatus(terminalStatus);
                row.setErrorMessage(errorMessage);
                row.setSchedulerInstanceId(safeSchedulerInstanceId(context));
                executionRepository.save(row);
                log.warn(
                        "Successfully saved {} record(s) to database (table={}, operation=INSERT, purpose=quartz execution audit repair, listenerPhase=jobWasExecuted, status={}). reason=no_prior_START_audit_row_for_fire_instance. job={} jobGroup={} fireInstanceId={} hes.quartz.listener phase=WAS_EXECUTED_REPAIR_INSERT",
                        1, TBL_EXECUTION, terminalStatus, jobName, jobGroup, fireInstanceId);
            }
        } catch (Exception e) {
            log.error(
                    "Error saving record(s) to database (table={}, operation=UPDATE_OR_INSERT, purpose=quartz execution audit, listenerPhase=jobWasExecuted). recordsSaved=0. job={} jobGroup={} fireInstanceId={}. cause={}",
                    TBL_EXECUTION, jobName, jobGroup, fireInstanceId, truncateMessage(e.getMessage()), e);
        }

        touchCatalogRow(context, terminalStatus, now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onJobExecutionVetoed(JobExecutionContext context) {
        String fireInstanceId = context.getFireInstanceId();
        LocalDateTime now = LocalDateTime.now();
        String jobName = context.getJobDetail().getKey().getName();
        String jobGroup = context.getJobDetail().getKey().getGroup();

        try {
            SchedulerJobExecution row = new SchedulerJobExecution();
            row.setFireInstanceId(fireInstanceId);
            row.setJobName(jobName);
            row.setJobGroup(jobGroup);
            row.setScheduledFireTime(toLocalDateTime(context.getScheduledFireTime()));
            row.setFireTime(toLocalDateTime(context.getFireTime()));
            row.setStartedAt(now);
            row.setEndedAt(now);
            row.setStatus(STATUS_VETOED);
            row.setSchedulerInstanceId(safeSchedulerInstanceId(context));
            executionRepository.save(row);
            log.info(
                    "Successfully saved {} record(s) to database (table={}, operation=INSERT, purpose=quartz execution audit veto, listenerPhase=jobExecutionVetoed, status={}). job={} jobGroup={} fireInstanceId={} hes.quartz.listener phase=LISTENER_VETOED_PERSISTED",
                    1, TBL_EXECUTION, STATUS_VETOED, jobName, jobGroup, fireInstanceId);
        } catch (Exception e) {
            log.error(
                    "Error saving record(s) to database (table={}, operation=INSERT, purpose=quartz execution audit veto, listenerPhase=jobExecutionVetoed). recordsSaved=0. job={} jobGroup={} fireInstanceId={}. cause={}",
                    TBL_EXECUTION, jobName, jobGroup, fireInstanceId, truncateMessage(e.getMessage()), e);
        }

        touchCatalogRow(context, STATUS_VETOED, now);
    }

    private void touchCatalogRow(JobExecutionContext context, String status, LocalDateTime lastRunTime) {
        String jobName = context.getJobDetail().getKey().getName();
        String jobGroup = context.getJobDetail().getKey().getGroup();
        String fireInstanceId = context.getFireInstanceId();
        try {
            Optional<SchedulerJobInfo> row = schedulerRepository.findByJobNameAndJobGroup(jobName, jobGroup);
            if (row.isEmpty()) {
                log.warn(
                        "No records saved to database (table={}, operation=UPDATE, purpose=scheduler job catalog). recordsSaved=0. reason=no_matching_catalog_row_for_job_key. job={} jobGroup={} fireInstanceId={} listenerStatus={} hes.quartz.listener phase=CATALOG_ROW_MISSING",
                        TBL_CATALOG, jobName, jobGroup, fireInstanceId, status);
                return;
            }
            SchedulerJobInfo jobInfo = row.get();
            jobInfo.setJobStatus(status);
            jobInfo.setLastRunTime(lastRunTime);
            schedulerRepository.save(jobInfo);
            log.info(
                    "Successfully saved {} record(s) to database (table={}, operation=UPDATE, purpose=scheduler job catalog, fields=job_status+last_run_time, listenerStatus={}). job={} jobGroup={} fireInstanceId={} hes.quartz.listener phase=LISTENER_CATALOG_UPDATED",
                    1, TBL_CATALOG, status, jobName, jobGroup, fireInstanceId);
        } catch (Exception e) {
            log.error(
                    "Error saving record(s) to database (table={}, operation=UPDATE, purpose=scheduler job catalog). recordsSaved=0. job={} jobGroup={} fireInstanceId={}. cause={}",
                    TBL_CATALOG, jobName, jobGroup, fireInstanceId, truncateMessage(e.getMessage()), e);
        }
    }

    private static Long toEpochMillis(Date d) {
        return d == null ? null : d.getTime();
    }

    private static LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    private static String safeSchedulerInstanceId(JobExecutionContext context) {
        try {
            return context.getScheduler().getSchedulerInstanceId();
        } catch (SchedulerException e) {
            return "_unknown";
        }
    }

    private static String buildErrorMessage(JobExecutionException jobException) {
        Throwable cause = jobException.getUnderlyingException() != null
                ? jobException.getUnderlyingException()
                : jobException;
        String msg = cause.getClass().getName() + ": " + cause.getMessage();
        return truncateMessage(msg);
    }

    private static String truncateMessage(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= ERROR_MSG_MAX ? message : message.substring(0, ERROR_MSG_MAX) + "...";
    }
}
