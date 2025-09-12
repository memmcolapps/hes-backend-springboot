package com.memmcol.hes.config;

import com.memmcol.hes.job.LoadProfileChannel1Job;
import jakarta.annotation.PostConstruct;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;


@Configuration
public class QuartzConfig {
    private final SchedulerFactoryBean schedulerFactoryBean;

    @Value("${quartz.profiles.load1}")
    private String load1Cron;

    public QuartzConfig(SchedulerFactoryBean schedulerFactoryBean) {
        this.schedulerFactoryBean = schedulerFactoryBean;
    }
    @Value("${quartz.profiles.load2}")
    private String load2Cron;

    @Value("${quartz.profiles.event}")
    private String eventCron;

    @Value("${quartz.profiles.dailyBilling}")
    private String dailyBillingCron;

    @Value("${quartz.profiles.monthlyBilling}")
    private String monthlyBillingCron;

    @PostConstruct
    public void scheduleJobs() throws SchedulerException {
        Scheduler scheduler = schedulerFactoryBean.getScheduler();

        // ✅ Define JobDetail
        JobDetail load1JobDetail = JobBuilder.newJob(LoadProfileChannel1Job.class)
                .withIdentity("load1Job", "HES_JOBS")
                .storeDurably()
                .build();

        // ✅ Define Trigger
        TriggerKey triggerKey = new TriggerKey("load1Trigger", "HES_TRIGGERS");

        Trigger load1Trigger = TriggerBuilder.newTrigger()
                .forJob(load1JobDetail)
                .withIdentity("load1Trigger", "HES_TRIGGERS")
                .withSchedule(CronScheduleBuilder.cronSchedule(load1Cron))
                .build();

        // ✅ Register job + trigger
        if (!scheduler.checkExists(load1JobDetail.getKey())) {
            // First time → save job + trigger
            scheduler.scheduleJob(load1JobDetail, load1Trigger);
        } else {
            // Job already exists → reschedule trigger using existing TriggerKey
            scheduler.rescheduleJob(triggerKey, load1Trigger);
        }
    }
}