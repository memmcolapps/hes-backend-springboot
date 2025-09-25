package com.memmcol.hes.schedulers;

import com.memmcol.hes.repository.SchedulerRepository;
import lombok.RequiredArgsConstructor;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class JobStatusListener implements JobListener {
    private final SchedulerRepository schedulerRepository;

    @Override
    public String getName() {
        return "GlobalJobStatusListener";
    }

    @Override
    public void jobToBeExecuted(JobExecutionContext context) {
        updateStatus(context, "RUNNING");
    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext context) {
        updateStatus(context, "VETOED");
    }

    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        if (jobException != null) {
            updateStatus(context, "FAILED");
        } else {
            updateStatus(context, "COMPLETED");
        }
    }

    private void updateStatus(JobExecutionContext context, String status) {
        String jobName = context.getJobDetail().getKey().getName();
        String jobGroup = context.getJobDetail().getKey().getGroup();

        schedulerRepository.findByJobNameAndJobGroup(jobName, jobGroup).ifPresent(jobInfo -> {
            jobInfo.setJobStatus(status);
            jobInfo.setLastRunTime(LocalDateTime.now());
            schedulerRepository.save(jobInfo);
        });
    }
}
