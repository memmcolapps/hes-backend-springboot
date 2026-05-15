package com.memmcol.hes.schedulers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Global Quartz job listener: structured logs + {@code scheduler_job_execution} row
 * for every fire, plus {@code scheduler_job_info} updates when catalog rows exist.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobStatusListener implements JobListener {

    public static final String MDC_FIRE_INSTANCE_ID = "hes.quartz.fireInstanceId";
    public static final String MDC_JOB_NAME = "hes.quartz.jobName";
    public static final String MDC_JOB_GROUP = "hes.quartz.jobGroup";

    private final QuartzExecutionAuditService quartzExecutionAuditService;

    @Override
    public String getName() {
        return "GlobalJobStatusListener";
    }

    @Override
    public void jobToBeExecuted(JobExecutionContext context) {
        openMdc(context);
        try {
            quartzExecutionAuditService.onJobToBeExecuted(context);
        } catch (Exception e) {
            log.error(
                    "hes.quartz.listener phase=TO_BE_EXECUTED_UNEXPECTED job={} jobGroup={} fireInstanceId={} errorType={} errorMessage={}",
                    context.getJobDetail().getKey().getName(),
                    context.getJobDetail().getKey().getGroup(),
                    context.getFireInstanceId(),
                    e.getClass().getName(),
                    e.getMessage(),
                    e);
        }
    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext context) {
        openMdc(context);
        try {
            quartzExecutionAuditService.onJobExecutionVetoed(context);
        } catch (Exception e) {
            log.error(
                    "hes.quartz.listener phase=VETOED_UNEXPECTED job={} jobGroup={} fireInstanceId={} errorType={} errorMessage={}",
                    context.getJobDetail().getKey().getName(),
                    context.getJobDetail().getKey().getGroup(),
                    context.getFireInstanceId(),
                    e.getClass().getName(),
                    e.getMessage(),
                    e);
        } finally {
            clearMdc();
        }
    }

    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        openMdc(context);
        try {
            quartzExecutionAuditService.onJobWasExecuted(context, jobException);
            if (jobException != null) {
                log.warn(
                        "hes.quartz.listener phase=JOB_EXCEPTION_PROPAGATED job={} jobGroup={} fireInstanceId={} quartzMessage={}",
                        context.getJobDetail().getKey().getName(),
                        context.getJobDetail().getKey().getGroup(),
                        context.getFireInstanceId(),
                        jobException.getMessage(),
                        jobException);
            }
        } catch (Exception e) {
            log.error(
                    "hes.quartz.listener phase=WAS_EXECUTED_UNEXPECTED job={} jobGroup={} fireInstanceId={} errorType={} errorMessage={}",
                    context.getJobDetail().getKey().getName(),
                    context.getJobDetail().getKey().getGroup(),
                    context.getFireInstanceId(),
                    e.getClass().getName(),
                    e.getMessage(),
                    e);
        } finally {
            clearMdc();
        }
    }

    private static void openMdc(JobExecutionContext context) {
        MDC.put(MDC_FIRE_INSTANCE_ID, context.getFireInstanceId());
        MDC.put(MDC_JOB_NAME, context.getJobDetail().getKey().getName());
        MDC.put(MDC_JOB_GROUP, context.getJobDetail().getKey().getGroup());
    }

    private static void clearMdc() {
        MDC.remove(MDC_FIRE_INSTANCE_ID);
        MDC.remove(MDC_JOB_NAME);
        MDC.remove(MDC_JOB_GROUP);
    }
}
