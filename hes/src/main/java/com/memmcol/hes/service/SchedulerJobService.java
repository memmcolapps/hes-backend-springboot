package com.memmcol.hes.service;

import java.util.Date;
import java.util.List;

import com.memmcol.hes.component.JobScheduleCreator;
import com.memmcol.hes.job.SampleCronJob;
import com.memmcol.hes.job.SimpleJob;
import com.memmcol.hes.model.SchedulerJobInfo;
import com.memmcol.hes.repository.SchedulerRepository;
import lombok.AllArgsConstructor;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Transactional
@Service
public class SchedulerJobService {

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private SchedulerFactoryBean schedulerFactoryBean;

    @Autowired
    private SchedulerRepository schedulerRepository;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private JobScheduleCreator scheduleCreator;

    public SchedulerMetaData getMetaData() throws SchedulerException {
        SchedulerMetaData metaData = scheduler.getMetaData();
        return metaData;
    }

    public List<SchedulerJobInfo> getAllJobList() {
        return schedulerRepository.findAll();
    }

    public boolean deleteJob(SchedulerJobInfo jobInfo) {
        try {
            SchedulerJobInfo getJobInfo = schedulerRepository.findByJobName(jobInfo.getJobName());
            schedulerRepository.delete(getJobInfo);
            log.info(">>>>> jobName = [" + jobInfo.getJobName() + "]" + " deleted.");
            return schedulerFactoryBean.getScheduler().deleteJob(new JobKey(jobInfo.getJobName(), jobInfo.getJobGroup()));
        } catch (SchedulerException e) {
            log.error("Failed to delete job - {}", jobInfo.getJobName(), e);
            return false;
        }
    }

    public boolean pauseJob(SchedulerJobInfo jobInfo) {
        try {
            SchedulerJobInfo getJobInfo = schedulerRepository.findByJobName(jobInfo.getJobName());
            getJobInfo.setJobStatus("PAUSED");
            schedulerRepository.save(getJobInfo);
            schedulerFactoryBean.getScheduler().pauseJob(new JobKey(jobInfo.getJobName(), jobInfo.getJobGroup()));
            log.info(">>>>> jobName = [" + jobInfo.getJobName() + "]" + " paused.");
            return true;
        } catch (SchedulerException e) {
            log.error("Failed to pause job - {}", jobInfo.getJobName(), e);
            return false;
        }
    }

    public boolean resumeJob(SchedulerJobInfo jobInfo) {
        try {
            SchedulerJobInfo getJobInfo = schedulerRepository.findByJobName(jobInfo.getJobName());
            getJobInfo.setJobStatus("RESUMED");
            schedulerRepository.save(getJobInfo);
            schedulerFactoryBean.getScheduler().resumeJob(new JobKey(jobInfo.getJobName(), jobInfo.getJobGroup()));
            log.info(">>>>> jobName = [" + jobInfo.getJobName() + "]" + " resumed.");
            return true;
        } catch (SchedulerException e) {
            log.error("Failed to resume job - {}", jobInfo.getJobName(), e);
            return false;
        }
    }

    public boolean startJobNow(SchedulerJobInfo jobInfo) {
        try {
            SchedulerJobInfo getJobInfo = schedulerRepository.findByJobName(jobInfo.getJobName());
            getJobInfo.setJobStatus("SCHEDULED & STARTED");
            schedulerRepository.save(getJobInfo);
            schedulerFactoryBean.getScheduler().triggerJob(new JobKey(jobInfo.getJobName(), jobInfo.getJobGroup()));
            log.info(">>>>> jobName = [" + jobInfo.getJobName() + "]" + " scheduled and started now.");
            return true;
        } catch (SchedulerException e) {
            log.error("Failed to start new job - {}", jobInfo.getJobName(), e);
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    public void saveOrupdate(SchedulerJobInfo scheduleJob) throws Exception {
        Scheduler scheduler = schedulerFactoryBean.getScheduler();

        // Determine job class only if not provided
        if (StringUtils.isEmpty(scheduleJob.getJobClass())) {
            if (!StringUtils.isEmpty(scheduleJob.getCronExpression())) {
                scheduleJob.setJobClass(SampleCronJob.class.getName());
            } else {
                scheduleJob.setJobClass(SimpleJob.class.getName());
            }
        }

        // Decide if it's a cron job
        scheduleJob.setCronJob(!StringUtils.isEmpty(scheduleJob.getCronExpression()));

        if (scheduleJob.getJobId() == null) {
            // New job
            scheduleNewJob(scheduleJob);
        } else {
            // Update existing job
            updateScheduleJob(scheduleJob);
        }

        log.info(">>>>> jobName = [{}] scheduled/updated with jobClass={}, description={}, interfaceName={}",
                scheduleJob.getJobName(),
                scheduleJob.getJobClass(),
                scheduleJob.getDescription(),
                scheduleJob.getInterfaceName());
    }

    @SuppressWarnings("unchecked")
    public void scheduleNewJob(SchedulerJobInfo jobInfo) {
        try {
            Scheduler scheduler = schedulerFactoryBean.getScheduler();

            // 1️⃣ Load the job class
            Class<? extends QuartzJobBean> jobClazz =
                    (Class<? extends QuartzJobBean>) Class.forName(jobInfo.getJobClass());

            // 2️⃣ Build JobDetail with only serializable JobDataMap
            JobDataMap jobDataMap = new JobDataMap();
            jobDataMap.put("description", jobInfo.getDescription());
            jobDataMap.put("interfaceName", jobInfo.getInterfaceName());

            JobDetail jobDetail = JobBuilder.newJob(jobClazz)
                    .withIdentity(jobInfo.getJobName(), jobInfo.getJobGroup())
                    .storeDurably()
                    .usingJobData(jobDataMap)
                    .build();

            // 3️⃣ Build Trigger
            Trigger trigger;
            if (jobInfo.getCronJob()) {
                trigger = TriggerBuilder.newTrigger()
                        .forJob(jobDetail)
                        .withIdentity(jobInfo.getJobName(), jobInfo.getJobGroup())
                        .usingJobData(jobDetail.getJobDataMap())
                        .withSchedule(CronScheduleBuilder.cronSchedule(jobInfo.getCronExpression())
                                .withMisfireHandlingInstructionFireAndProceed())
                        .build();
            } else {
                trigger = TriggerBuilder.newTrigger()
                        .forJob(jobDetail)
                        .withIdentity(jobInfo.getJobName(), jobInfo.getJobGroup())
                        .startNow()
                        .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                                .withIntervalInMilliseconds(jobInfo.getRepeatTime())
                                .repeatForever()
                                .withMisfireHandlingInstructionFireNow())
                        .build();
            }

            // 4️⃣ Schedule only if not exists
            if (!scheduler.checkExists(jobDetail.getKey())) {
                scheduler.scheduleJob(jobDetail, trigger);

                // Persist job info exactly as provided
                jobInfo.setJobStatus("SCHEDULED");
                schedulerRepository.save(jobInfo);

                log.info(">>>>> jobName = [{}] scheduled with jobClass={}, description={}, interfaceName={}",
                        jobInfo.getJobName(),
                        jobInfo.getJobClass(),
                        jobInfo.getDescription(),
                        jobInfo.getInterfaceName()
                );
            } else {
                log.warn("Job [{}] already exists. Skipping creation.", jobInfo.getJobName());
            }

        } catch (ClassNotFoundException e) {
            log.error("Class Not Found - {}", jobInfo.getJobClass(), e);
        } catch (SchedulerException e) {
            log.error("Scheduler exception for job [{}]: {}", jobInfo.getJobName(), e.getMessage(), e);
        }
    }

    private void updateScheduleJob(SchedulerJobInfo jobInfo) {
        Trigger newTrigger;
        if (jobInfo.getCronJob()) {
            newTrigger = scheduleCreator.createCronTrigger(jobInfo.getJobName(), new Date(),
                    jobInfo.getCronExpression(), SimpleTrigger.MISFIRE_INSTRUCTION_FIRE_NOW);
        } else {
            newTrigger = scheduleCreator.createSimpleTrigger(jobInfo.getJobName(), new Date(), jobInfo.getRepeatTime(),
                    SimpleTrigger.MISFIRE_INSTRUCTION_FIRE_NOW);
        }
        try {
            schedulerFactoryBean.getScheduler().rescheduleJob(TriggerKey.triggerKey(jobInfo.getJobName()), newTrigger);
            jobInfo.setJobStatus("EDITED & SCHEDULED");
            schedulerRepository.save(jobInfo);
            log.info(">>>>> jobName = [" + jobInfo.getJobName() + "]" + " updated and scheduled.");
        } catch (SchedulerException e) {
            log.error(e.getMessage(), e);
        }
    }


    @SuppressWarnings("unchecked")
    public void scheduleJobWithoutData(SchedulerJobInfo jobInfo) {
        try {
            Scheduler scheduler = schedulerFactoryBean.getScheduler();
            JobKey jobKey = JobKey.jobKey(jobInfo.getJobName(), jobInfo.getJobGroup());
            TriggerKey triggerKey = TriggerKey.triggerKey(jobInfo.getJobName(), jobInfo.getJobGroup());

            // 1️⃣ Load the job class
            Class<? extends QuartzJobBean> jobClazz =
                    (Class<? extends QuartzJobBean>) Class.forName(jobInfo.getJobClass());

            // 2️⃣ Create JobDetail WITHOUT JobDataMap
            JobDetail jobDetail;
            if (!scheduler.checkExists(jobKey)) {
                jobDetail = JobBuilder.newJob(jobClazz)
                        .withIdentity(jobKey)
                        .storeDurably()
                        .build();
                scheduler.addJob(jobDetail, true);
            } else {
                jobDetail = scheduler.getJobDetail(jobKey);
            }

            // 3️⃣ Create Trigger WITHOUT JobDataMap
            Trigger trigger;
            if (jobInfo.getCronJob()) {
                trigger = TriggerBuilder.newTrigger()
                        .forJob(jobKey)
                        .withIdentity(triggerKey)
                        .withSchedule(CronScheduleBuilder.cronSchedule(jobInfo.getCronExpression())
                                .withMisfireHandlingInstructionFireAndProceed())
                        .build();
            } else {
                trigger = TriggerBuilder.newTrigger()
                        .forJob(jobKey)
                        .withIdentity(triggerKey)
                        .startNow()
                        .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                                .withIntervalInMilliseconds(jobInfo.getRepeatTime())
                                .repeatForever()
                                .withMisfireHandlingInstructionFireNow())
                        .build();
            }

            // 4️⃣ Unschedule old trigger if exists
            if (scheduler.checkExists(triggerKey)) {
                scheduler.unscheduleJob(triggerKey);
            }

            // 5️⃣ Schedule new trigger
            scheduler.scheduleJob(trigger);

            // 6️⃣ Persist jobInfo
            jobInfo.setJobStatus("SCHEDULED");
            schedulerRepository.save(jobInfo);

            log.info(">>>>> jobName = [{}] scheduled successfully with jobClass={}, job_data set to NULL",
                    jobInfo.getJobName(), jobInfo.getJobClass());

        } catch (ClassNotFoundException e) {
            log.error("Class Not Found - {}", jobInfo.getJobClass(), e);
        } catch (SchedulerException e) {
            log.error("Scheduler exception for job [{}]: {}", jobInfo.getJobName(), e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public void updateOrCreateJob(SchedulerJobInfo jobInfo) {
        try {
            Scheduler scheduler = schedulerFactoryBean.getScheduler();
            JobKey jobKey = JobKey.jobKey(jobInfo.getJobName(), jobInfo.getJobGroup());
            TriggerKey triggerKey = TriggerKey.triggerKey(jobInfo.getJobName(), jobInfo.getJobGroup());

            // 1️⃣ Ensure JobDataMap contains only serializable values
            JobDataMap jobDataMap = new JobDataMap();
            jobDataMap.put("description", jobInfo.getDescription());
            jobDataMap.put("interfaceName", jobInfo.getInterfaceName());

            // 2️⃣ Load the job class
            Class<? extends QuartzJobBean> jobClazz = (Class<? extends QuartzJobBean>) Class.forName(jobInfo.getJobClass());

            // 3️⃣ Create or update JobDetail
            JobDetail jobDetail;
            if (!scheduler.checkExists(jobKey)) {
                // Job does not exist → create new JobDetail
                jobDetail = JobBuilder.newJob(jobClazz)
                        .withIdentity(jobKey)
                        .storeDurably()
                        .build();
                scheduler.addJob(jobDetail, true); // Add new job
            } else {
                // Job exists → update JobDetail's JobDataMap
                jobDetail = scheduler.getJobDetail(jobKey);
                jobDetail.getJobDataMap().clear();
                jobDetail.getJobDataMap().putAll(jobDataMap);
                scheduler.addJob(jobDetail, true); // Update existing job
            }

            // 4️⃣ Build Trigger
            Trigger newTrigger;
            if (jobInfo.getCronJob()) {
                newTrigger = TriggerBuilder.newTrigger()
                        .forJob(jobKey)
                        .withIdentity(triggerKey)
                        .withSchedule(CronScheduleBuilder.cronSchedule(jobInfo.getCronExpression())
                                .withMisfireHandlingInstructionFireAndProceed())
                        .build();
            } else {
                newTrigger = TriggerBuilder.newTrigger()
                        .forJob(jobKey)
                        .withIdentity(triggerKey)
                        .usingJobData(jobDetail.getJobDataMap())
                        .startNow()
                        .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                                .withIntervalInMilliseconds(jobInfo.getRepeatTime())
                                .repeatForever()
                                .withMisfireHandlingInstructionFireNow())
                        .build();
            }

            // 5️⃣ Unschedule old trigger if exists
            if (scheduler.checkExists(triggerKey)) {
                scheduler.unscheduleJob(triggerKey);
            }

            // 6️⃣ Schedule new trigger
            scheduler.scheduleJob(newTrigger);

            // 7️⃣ Persist jobInfo
            jobInfo.setJobStatus("SCHEDULED");
            schedulerRepository.save(jobInfo);

            log.info(">>>>> jobName = [{}] scheduled/updated successfully with jobClass={}, description={}, interfaceName={}",
                    jobInfo.getJobName(), jobInfo.getJobClass(), jobInfo.getDescription(), jobInfo.getInterfaceName());

        } catch (ClassNotFoundException e) {
            log.error("Class Not Found - {}", jobInfo.getJobClass(), e);
        } catch (SchedulerException e) {
            log.error("Scheduler exception for job [{}]: {}", jobInfo.getJobName(), e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public void scheduleNewJob2(SchedulerJobInfo jobInfo) {
        try {
            Scheduler scheduler = schedulerFactoryBean.getScheduler();

            // 1️⃣ Load the job class
            Class<? extends QuartzJobBean> jobClazz =
                    (Class<? extends QuartzJobBean>) Class.forName(jobInfo.getJobClass());

            // 2️⃣ Create a clean JobDataMap with only serializable fields
            JobDataMap jobDataMap = new JobDataMap();
            jobDataMap.put("description", jobInfo.getDescription());
            jobDataMap.put("interfaceName", jobInfo.getInterfaceName());

            // 3️⃣ Build JobDetail
            JobDetail jobDetail = JobBuilder.newJob(jobClazz)
                    .withIdentity(jobInfo.getJobName(), jobInfo.getJobGroup())
                    .storeDurably()
                    .usingJobData(jobDataMap)  // ✅ only safe data
                    .build();

            // 4️⃣ Build Trigger
            Trigger trigger;
            if (jobInfo.getCronJob()) {
                trigger = TriggerBuilder.newTrigger()
                        .forJob(jobDetail)
                        .withIdentity(jobInfo.getJobName(), jobInfo.getJobGroup())
                        .usingJobData(jobDetail.getJobDataMap())  // safe copy
                        .withSchedule(CronScheduleBuilder.cronSchedule(jobInfo.getCronExpression())
                                .withMisfireHandlingInstructionFireAndProceed())
                        .build();
            } else {
                trigger = TriggerBuilder.newTrigger()
                        .forJob(jobDetail)
                        .withIdentity(jobInfo.getJobName(), jobInfo.getJobGroup())
                        .startNow()
                        .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                                .withIntervalInMilliseconds(jobInfo.getRepeatTime())
                                .repeatForever()
                                .withMisfireHandlingInstructionFireNow())
                        .build();
            }

            // 5️⃣ Delete any existing job & trigger first to avoid duplicates
            if (scheduler.checkExists(jobDetail.getKey())) {
                scheduler.deleteJob(jobDetail.getKey());
            }

            // 6️⃣ Schedule new job + trigger
            scheduler.scheduleJob(jobDetail, trigger);

            // 7️⃣ Persist job info safely
            jobInfo.setJobStatus("SCHEDULED");
            schedulerRepository.save(jobInfo);

            log.info(">>>>> job [{}] scheduled with jobClass={}, description={}, interfaceName={}",
                    jobInfo.getJobName(),
                    jobInfo.getJobClass(),
                    jobInfo.getDescription(),
                    jobInfo.getInterfaceName());

        } catch (ClassNotFoundException e) {
            log.error("Class Not Found - {}", jobInfo.getJobClass(), e);
        } catch (SchedulerException e) {
            log.error("Scheduler exception for job [{}]: {}", jobInfo.getJobName(), e.getMessage(), e);
        }
    }
    @SuppressWarnings("unchecked")
    public void updateScheduleJob2(SchedulerJobInfo jobInfo) {
        try {
            Scheduler scheduler = schedulerFactoryBean.getScheduler();

            // 1️⃣ Build clean JobDataMap
            JobDataMap jobDataMap = new JobDataMap();
            jobDataMap.put("description", jobInfo.getDescription());
            jobDataMap.put("interfaceName", jobInfo.getInterfaceName());

            // 2️⃣ Build new trigger
            Trigger newTrigger;
            if (jobInfo.getCronJob()) {
                newTrigger = TriggerBuilder.newTrigger()
                        .withIdentity(jobInfo.getJobName(), jobInfo.getJobGroup())
                        .usingJobData(jobDataMap)
                        .withSchedule(CronScheduleBuilder.cronSchedule(jobInfo.getCronExpression())
                                .withMisfireHandlingInstructionFireAndProceed())
                        .forJob(jobInfo.getJobName(), jobInfo.getJobGroup())
                        .build();
            } else {
                newTrigger = TriggerBuilder.newTrigger()
                        .withIdentity(jobInfo.getJobName(), jobInfo.getJobGroup())
                        .usingJobData(jobDataMap)
                        .startNow()
                        .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                                .withIntervalInMilliseconds(jobInfo.getRepeatTime())
                                .repeatForever()
                                .withMisfireHandlingInstructionFireNow())
                        .forJob(jobInfo.getJobName(), jobInfo.getJobGroup())
                        .build();
            }

            // 3️⃣ Reschedule trigger safely
            scheduler.rescheduleJob(TriggerKey.triggerKey(jobInfo.getJobName(), jobInfo.getJobGroup()), newTrigger);

            // 4️⃣ Persist updated job info
            jobInfo.setJobStatus("EDITED & SCHEDULED");
            schedulerRepository.save(jobInfo);

            log.info(">>>>> job [{}] updated and scheduled.", jobInfo.getJobName());

        } catch (SchedulerException e) {
            log.error("Error updating job [{}]: {}", jobInfo.getJobName(), e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public void scheduleNewJob3(SchedulerJobInfo jobInfo) {
        try {
            Scheduler scheduler = schedulerFactoryBean.getScheduler();

            // 1️⃣ Load the job class
            Class<? extends QuartzJobBean> jobClazz =
                    (Class<? extends QuartzJobBean>) Class.forName(jobInfo.getJobClass());

            // 2️⃣ Create a clean JobDataMap with only serializable fields
            JobDataMap jobDataMap = new JobDataMap();
            jobDataMap.put("description", jobInfo.getDescription());
            jobDataMap.put("interfaceName", jobInfo.getInterfaceName());

            // 3️⃣ Build JobDetail
            JobDetail jobDetail = JobBuilder.newJob(jobClazz)
                    .withIdentity(jobInfo.getJobName(), jobInfo.getJobGroup())
                    .storeDurably()
                    .usingJobData(jobDataMap)  // ✅ only safe data
                    .build();

            // 4️⃣ Build Trigger
            Trigger trigger;
            if (jobInfo.getCronJob()) {
                trigger = TriggerBuilder.newTrigger()
                        .forJob(jobDetail)
                        .withIdentity(jobInfo.getJobName(), jobInfo.getJobGroup())
                        .usingJobData(jobDetail.getJobDataMap())  // safe copy
                        .withSchedule(CronScheduleBuilder.cronSchedule(jobInfo.getCronExpression())
                                .withMisfireHandlingInstructionFireAndProceed())
                        .build();
            } else {
                trigger = TriggerBuilder.newTrigger()
                        .forJob(jobDetail)
                        .withIdentity(jobInfo.getJobName(), jobInfo.getJobGroup())
                        .startNow()
                        .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                                .withIntervalInMilliseconds(jobInfo.getRepeatTime())
                                .repeatForever()
                                .withMisfireHandlingInstructionFireNow())
                        .build();
            }

            // 5️⃣ Delete any existing job & trigger first to avoid duplicates
            if (scheduler.checkExists(jobDetail.getKey())) {
                scheduler.deleteJob(jobDetail.getKey());
            }

            // 6️⃣ Schedule new job + trigger
            scheduler.scheduleJob(jobDetail, trigger);

            // 7️⃣ Persist job info safely
            jobInfo.setJobStatus("SCHEDULED");
            schedulerRepository.save(jobInfo);

            log.info(">>>>> job [{}] scheduled with jobClass={}, description={}, interfaceName={}",
                    jobInfo.getJobName(),
                    jobInfo.getJobClass(),
                    jobInfo.getDescription(),
                    jobInfo.getInterfaceName());

        } catch (ClassNotFoundException e) {
            log.error("Class Not Found - {}", jobInfo.getJobClass(), e);
        } catch (SchedulerException e) {
            log.error("Scheduler exception for job [{}]: {}", jobInfo.getJobName(), e.getMessage(), e);
        }
    }


//        ✅ updateScheduleJob()

    @SuppressWarnings("unchecked")
    public void updateScheduleJob3(SchedulerJobInfo jobInfo) {
        try {
            Scheduler scheduler = schedulerFactoryBean.getScheduler();

            // 1️⃣ Build clean JobDataMap
            JobDataMap jobDataMap = new JobDataMap();
            jobDataMap.put("description", jobInfo.getDescription());
            jobDataMap.put("interfaceName", jobInfo.getInterfaceName());

            // 2️⃣ Build new trigger
            Trigger newTrigger;
            if (jobInfo.getCronJob()) {
                newTrigger = TriggerBuilder.newTrigger()
                        .withIdentity(jobInfo.getJobName(), jobInfo.getJobGroup())
                        .usingJobData(jobDataMap)
                        .withSchedule(CronScheduleBuilder.cronSchedule(jobInfo.getCronExpression())
                                .withMisfireHandlingInstructionFireAndProceed())
                        .forJob(jobInfo.getJobName(), jobInfo.getJobGroup())
                        .build();
            } else {
                newTrigger = TriggerBuilder.newTrigger()
                        .withIdentity(jobInfo.getJobName(), jobInfo.getJobGroup())
                        .usingJobData(jobDataMap)
                        .startNow()
                        .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                                .withIntervalInMilliseconds(jobInfo.getRepeatTime())
                                .repeatForever()
                                .withMisfireHandlingInstructionFireNow())
                        .forJob(jobInfo.getJobName(), jobInfo.getJobGroup())
                        .build();
            }

            // 3️⃣ Reschedule trigger safely
            scheduler.rescheduleJob(TriggerKey.triggerKey(jobInfo.getJobName(), jobInfo.getJobGroup()), newTrigger);

            // 4️⃣ Persist updated job info
            jobInfo.setJobStatus("EDITED & SCHEDULED");
            schedulerRepository.save(jobInfo);

            log.info(">>>>> job [{}] updated and scheduled.", jobInfo.getJobName());

        } catch (SchedulerException e) {
            log.error("Error updating job [{}]: {}", jobInfo.getJobName(), e.getMessage(), e);
        }
    }



}
