package com.memmcol.hes.schedulers;

import org.quartz.JobExecutionContext;
import org.slf4j.Logger;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;

/**
 * Single structured log format for all Quartz job bodies (grep/SIEM friendly).
 */
public final class QuartzExecutionLogging {

    private QuartzExecutionLogging() {
    }

    /**
     * Log at the start of {@code executeInternal} — same pattern for every job class.
     */
    public static void logJobExecuteStart(Logger log, JobExecutionContext context) {
        logJobExecuteStart(log, context, null);
    }

    public static void logJobExecuteStart(Logger log, JobExecutionContext context, String obisOrNull) {
        logJobExecuteStart(log, context, obisOrNull, null);
    }

    /**
     * @param extraKvs optional space-separated key=value pairs (e.g. {@code meterSerial=123 model=ABC})
     */
    public static void logJobExecuteStart(Logger log, JobExecutionContext context, String obisOrNull, String extraKvs) {
        if (!log.isInfoEnabled()) {
            return;
        }
        String obisPart = obisOrNull == null || obisOrNull.isBlank()
                ? "obis=_none"
                : "obis=" + escapeLogValue(obisOrNull);
        String extraPart = extraKvs == null || extraKvs.isBlank() ? "" : " " + extraKvs.trim();
        log.info(
                "hes.quartz.job phase=EXECUTE_BODY job={} jobGroup={} fireInstanceId={} scheduledFireTime={} fireTime={} {}{}",
                escapeLogValue(context.getJobDetail().getKey().getName()),
                escapeLogValue(context.getJobDetail().getKey().getGroup()),
                escapeLogValue(context.getFireInstanceId()),
                formatDate(context.getScheduledFireTime()),
                formatDate(context.getFireTime()),
                obisPart,
                extraPart);
    }

    public static void logJobExecuteFailure(Logger log, JobExecutionContext context, Throwable error) {
        log.error(
                "hes.quartz.job phase=EXECUTE_BODY_FAILED job={} jobGroup={} fireInstanceId={} scheduledFireTime={} fireTime={} errorType={} errorMessage={}",
                escapeLogValue(context.getJobDetail().getKey().getName()),
                escapeLogValue(context.getJobDetail().getKey().getGroup()),
                escapeLogValue(context.getFireInstanceId()),
                formatDate(context.getScheduledFireTime()),
                formatDate(context.getFireTime()),
                error.getClass().getName(),
                escapeLogValue(truncate(error.getMessage(), 500)),
                error);
    }

    private static String formatDate(Date d) {
        if (d == null) {
            return "_null";
        }
        return Instant.ofEpochMilli(d.getTime()).atZone(ZoneId.systemDefault()).toLocalDateTime().toString();
    }

    private static String escapeLogValue(String s) {
        if (s == null) {
            return "_null";
        }
        return s.replace('\n', ' ').replace('\r', ' ');
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
