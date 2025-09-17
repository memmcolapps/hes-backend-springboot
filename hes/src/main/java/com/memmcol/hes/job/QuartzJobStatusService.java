package com.memmcol.hes.job;

import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class QuartzJobStatusService {

    private final Scheduler scheduler;

    public QuartzJobStatusService(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Get the status of a specific job
     */
    public String getJobStatus(String jobName, String jobGroup) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);

        if (!scheduler.checkExists(jobKey)) {
            return "NOT_FOUND";
        }

        List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
        if (triggers == null || triggers.isEmpty()) {
            return "NO_TRIGGER";
        }

        for (Trigger trigger : triggers) {
            Trigger.TriggerState state = scheduler.getTriggerState(trigger.getKey());

            return switch (state) {
                case NONE -> "NONE";
                case NORMAL -> "RUNNING";
                case PAUSED -> "PAUSED";
                case COMPLETE -> "COMPLETED";
                case ERROR -> "ERROR";
                case BLOCKED -> "BLOCKED";
            };
        }

        return "UNKNOWN";
    }

    /**
     * Get all jobs with their statuses
     */
    public Map<String, String> getAllJobStatuses() throws SchedulerException {
        Map<String, String> result = new HashMap<>();

        for (String groupName : scheduler.getJobGroupNames()) {
            for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName))) {
                result.put(jobKey.toString(), getJobStatus(jobKey.getName(), jobKey.getGroup()));
            }
        }

        return result;
    }


    public void pauseJob(String jobName, String jobGroup) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
        if (scheduler.checkExists(jobKey)) {
            scheduler.pauseJob(jobKey);
        } else {
            throw new SchedulerException("Job not found: " + jobKey);
        }
    }

    public void resumeJob(String jobName, String jobGroup) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
        if (scheduler.checkExists(jobKey)) {
            scheduler.resumeJob(jobKey);
        } else {
            throw new SchedulerException("Job not found: " + jobKey);
        }
    }

    public void deleteJob(String jobName, String jobGroup) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
        if (scheduler.checkExists(jobKey)) {
            scheduler.deleteJob(jobKey);
        } else {
            throw new SchedulerException("Job not found: " + jobKey);
        }
    }


    public void triggerNow(String jobName, String jobGroup) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
        if (scheduler.checkExists(jobKey)) {
            scheduler.triggerJob(jobKey);
        } else {
            throw new SchedulerException("Job not found: " + jobKey);
        }
    }

}