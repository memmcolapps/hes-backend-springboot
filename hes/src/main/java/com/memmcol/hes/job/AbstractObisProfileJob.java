package com.memmcol.hes.job;

import com.memmcol.hes.nettyUtils.RequestResponseService;
import com.memmcol.hes.repository.MeterRepository;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

@Slf4j
@DisallowConcurrentExecution
@RequiredArgsConstructor
public abstract class AbstractObisProfileJob extends QuartzJobBean {
    protected final RequestResponseService txRxService;
    protected final MeterRepository meterRepo; // To fetch meters

    protected abstract void executeProfile(JobExecutionContext context);

    @Override
    protected void executeInternal(JobExecutionContext context) {
        try {
            executeProfile(context);
        } catch (Exception e) {
            log.error("Error executing job {}: {}", context.getJobDetail().getKey(), e.getMessage(), e);
        }
    }
}
