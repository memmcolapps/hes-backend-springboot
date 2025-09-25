package com.memmcol.hes.jobs.skeleton;

import lombok.RequiredArgsConstructor;
import org.quartz.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class JobControllerv1 {
    private final Scheduler scheduler;

    @PostMapping("/run")
    public String runJobNow(@RequestParam String jobName,
                            @RequestParam String jobGroup) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);

        if (!scheduler.checkExists(jobKey)) {
            return "❌ Job not found: " + jobGroup + "/" + jobName;
        }

        scheduler.triggerJob(jobKey);
        return "▶ Triggered job: " + jobGroup + "/" + jobName;
    }

    @PostMapping("/schedule")
    public String scheduleJob() throws SchedulerException {
        JobDetail jobDetail = JobBuilder.newJob(Channel1Jobv1.class)
                .withIdentity("Channel1Jobv1", "profiles")
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("Channel1Triggerv1", "profiles")
                .startNow()
                .withSchedule(SimpleScheduleBuilder.repeatSecondlyForever(30))
                .build();

        scheduler.scheduleJob(jobDetail, trigger);
        return "✅ Scheduled Channel1Jobv1 every 30s";
    }
}
