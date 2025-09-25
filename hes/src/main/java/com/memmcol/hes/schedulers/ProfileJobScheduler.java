package com.memmcol.hes.schedulers;

import com.memmcol.hes.jobs.*;
import com.memmcol.hes.model.SchedulerJobInfo;
import com.memmcol.hes.repository.SchedulerRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

//@Component
@RequiredArgsConstructor
@Deprecated
public class ProfileJobScheduler {
    private final SchedulerRepository schedulerRepository;
    private Scheduler scheduler;
    private final SchedulerFactoryBean schedulerFactoryBean;

    @Value("${cron.custom.interval1}")
    String cron1;

    @PostConstruct
    public void scheduleRobinJobs() throws SchedulerException {
//        this.scheduler = StdSchedulerFactory.getDefaultScheduler();
        this.scheduler = schedulerFactoryBean.getScheduler();

        // Define job classes in round-robin order
        List<Class<? extends Job>> jobs = Arrays.asList(
                Channel1Job.class,
                Channel2Job.class,
                EventsJob.class,
                DailyBillingJob.class,
                MonthlyBillingJob.class,
                HourlyJobs.class
        );

        // Define cron expressions (staggered hours, repeat daily)
        List<String> crons = Arrays.asList(
                "0 0 1,4,7,10,13,16,19,22 * * ?",    // Channel1 at 1am, 4am, 7am... except 12am
                "0 0 2,5,8,11,14,17,20,23 * * ?",    // Channel2 at 2am, 5am, 8am... except 12am
                "0 0 3,6,9,12,15,18,21 * * ?",      // Events at 3am, 6am, 9am... except 12am
                "0 10 0 * * ?",                     // Daily billing @ 12:10 AM every day
                "0 30 0 1 * ?",                      // Monthly billing @ 12:30 AM on day 1 of every month
                "0 0 1-23 * * ?"                    // every hour except 0:00
        );

        // Schedule each job
        for (int i = 0; i < jobs.size(); i++) {
            Class<? extends Job> jobClass = jobs.get(i);
            String cron = crons.get(i);
            String jobName = jobClass.getSimpleName();
            JobKey jobKey = JobKey.jobKey(jobName, "profiles");


            // 🔎 Skip if already in Quartz
            if (scheduler.checkExists(jobKey)) {
                continue;
            }

            // 🔎 Skip if already in scheduler_job_info
            if (schedulerRepository.findByJobNameAndJobGroup(jobName, "profiles").isPresent()) {
                continue;
            }

            JobDetail jobDetail = JobBuilder.newJob(jobClass)
                    .withIdentity(jobName, "profiles")
                    .build();

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(jobName + "Trigger", "profiles")
                    .withSchedule(CronScheduleBuilder.cronSchedule(cron))
                    .build();

            scheduler.scheduleJob(jobDetail, trigger);

             // Save to DB
            SchedulerJobInfo jobInfo = new SchedulerJobInfo();
            jobInfo.setJobName(jobName);
            jobInfo.setJobGroup("profiles");
            jobInfo.setJobClass(jobClass.getName());
            jobInfo.setCronExpression(cron);
            jobInfo.setJobStatus("SCHEDULED");
            jobInfo.setCronJob(true);
            jobInfo.setRepeatTime(0L);
            jobInfo.setRepeatSeconds(0);
            jobInfo.setRepeatMinutes(0);
            jobInfo.setRepeatHours(0);
            jobInfo.setInterfaceName("Profile Jobs");
            if (Set.of(Channel1Job.class, Channel2Job.class, EventsJob.class).contains(jobClass)) {
                jobInfo.setDescription("Round-robin scheduled job: " + jobName);
            } else if (Set.of(DailyBillingJob.class, MonthlyBillingJob.class, HourlyJobs.class).contains(jobClass)) {
                jobInfo.setDescription("Interval scheduled job: " + jobName);
            } else {
                jobInfo.setDescription("Other scheduled job: " + jobName);
            }

//            jobInfo.setDescription("Round-robin scheduled job: " + jobName);

            schedulerRepository.save(jobInfo);
        }

        scheduler.start();
    }

    public void scheduleRobinJobs_Old() throws SchedulerException {
        this.scheduler = StdSchedulerFactory.getDefaultScheduler();

        // -------------------------
        // Billing job at 12:00 am
        // -------------------------
        JobDetail billingJob = JobBuilder.newJob(DailyBillingJob.class)
                .withIdentity("billingJob", "profiles")
                .build();

        Trigger billingTrigger = TriggerBuilder.newTrigger()
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 0 * * ?")) // 12:00 am daily
                .build();

        scheduler.scheduleJob(billingJob, billingTrigger);

        // -------------------------
        // Channel 1 every 3 hours (starting 1am)
        // -------------------------
        JobDetail channel1Job = JobBuilder.newJob(Channel1Job.class)
                .withIdentity("channel1Job", "profiles")
                .build();

        Trigger channel1Trigger = TriggerBuilder.newTrigger()
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 1,4,7,10,13,16,19,22 * * ?"))
                .build();

        scheduler.scheduleJob(channel1Job, channel1Trigger);

        // -------------------------
        // Channel 2 every 3 hours (starting 2am)
        // -------------------------
        JobDetail channel2Job = JobBuilder.newJob(Channel2Job.class)
                .withIdentity("channel2Job", "profiles")
                .build();

        Trigger channel2Trigger = TriggerBuilder.newTrigger()
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 2,5,8,11,14,17,20,23 * * ?"))
                .build();

        scheduler.scheduleJob(channel2Job, channel2Trigger);

        // -------------------------
        // Events every 3 hours (starting 3am)
        // -------------------------
        JobDetail eventsJob = JobBuilder.newJob(EventsJob.class)
                .withIdentity("eventsJob", "profiles")
                .build();

        Trigger eventsTrigger = TriggerBuilder.newTrigger()
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 3,6,9,12,15,18,21 * * ?"))
                .build();

        scheduler.scheduleJob(eventsJob, eventsTrigger);

        // Start scheduler
        scheduler.start();
    }

//    @PostConstruct
    public void scheduleHourlyJobs() throws SchedulerException {
        this.scheduler = StdSchedulerFactory.getDefaultScheduler();
//        String jobName = jobClass.getSimpleName();
//        JobKey jobKey = JobKey.jobKey(jobName, "profiles");
//
//        // 🔎 Skip if already in Quartz
//        if (scheduler.checkExists(jobKey)) {
//            continue;
//        }
//
//        // 🔎 Skip if already in scheduler_job_info
//        if (schedulerRepository.findByJobNameAndJobGroup(jobName, "profiles").isPresent()) {
//            continue;
//        }

        // Parent hourly job
        JobDetail parentJob = JobBuilder.newJob(HourlyProfileParentJob.class)
                .withIdentity("hourlyJob2", "profiles")
                .build();

        /*	•	1-23 means hours from 1am to 11pm.
	        •	Midnight (0) is excluded.
	       */
        Trigger hourlyTrigger = TriggerBuilder.newTrigger()
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 1-23 * * ?")) // every hour except 0:00
                .build();

        scheduler.scheduleJob(parentJob, hourlyTrigger);


        // Daily/monthly billing at 12am
        JobDetail billingJob = JobBuilder.newJob(DailyBillingJob.class)
                .withIdentity("billingJob2", "profiles")
                .build();

        Trigger dailyTrigger = TriggerBuilder.newTrigger()
                .withSchedule(CronScheduleBuilder.dailyAtHourAndMinute(0, 0)) // 12:00am
                .build();

        scheduler.scheduleJob(billingJob, dailyTrigger);

        scheduler.start();
    }


}
