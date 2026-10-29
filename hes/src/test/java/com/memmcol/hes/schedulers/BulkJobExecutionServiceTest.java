package com.memmcol.hes.schedulers;

import com.memmcol.hes.controller.QuartzJobController;
import com.memmcol.hes.domain.profile.MetersLockService;
import com.memmcol.hes.dto.BulkTriggerRequest;
import com.memmcol.hes.dto.MeterDTO;
import com.memmcol.hes.entities.BulkJobExecution;
import com.memmcol.hes.entities.BulkJobMeterLog;
import com.memmcol.hes.model.SchedulerJobInfo;
import com.memmcol.hes.repository.BulkJobExecutionRepository;
import com.memmcol.hes.repository.BulkJobMeterLogRepository;
import com.memmcol.hes.repository.MeterRepository;
import com.memmcol.hes.repository.SchedulerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.Scheduler;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BulkJobExecutionServiceTest {

    @Mock
    private SchedulerRepository schedulerRepository;
    @Mock
    private MeterRepository meterRepository;
    @Mock
    private BulkJobExecutionRepository bulkJobExecutionRepository;
    @Mock
    private BulkJobMeterLogRepository bulkJobMeterLogRepository;
    @Mock
    private MetersLockService metersLockService;
    @Mock
    private Scheduler scheduler;
    @Mock
    private QuartzJobService quartzJobService;

    @InjectMocks
    private BulkJobExecutionService bulkJobExecutionService;

    private QuartzJobController controller;
    private com.memmcol.hes.controller.BatchJobController batchController;

    @BeforeEach
    void setUp() {
        controller = new QuartzJobController(quartzJobService, bulkJobExecutionService);
        batchController = new com.memmcol.hes.controller.BatchJobController(bulkJobExecutionService);
    }

    @Test
    void batchController_readBatch_Success() {
        SchedulerJobInfo jobInfo = new SchedulerJobInfo();
        jobInfo.setJobName("Channel1Job");
        jobInfo.setJobGroup("profile");
        jobInfo.setJobClass("com.memmcol.hes.jobs.Channel1Job");

        when(schedulerRepository.findByJobNameAndJobGroup("Channel1Job", "profile"))
                .thenReturn(Optional.of(jobInfo));

        BulkJobExecution execution = new BulkJobExecution();
        execution.setId(102L);
        execution.setJobName("Channel1Job");
        execution.setJobGroup("profile");
        execution.setStatus("RUNNING");
        execution.setIntervalType("HOURLY");

        when(bulkJobExecutionRepository.saveAndFlush(any(BulkJobExecution.class)))
                .thenReturn(execution);

        BulkTriggerRequest request = BulkTriggerRequest.builder()
                .meterSerials(List.of("625250000001"))
                .build();

        ResponseEntity<?> response = batchController.readBatch("Channel1Job", "profile", request);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> map = (Map<?, ?>) response.getBody();
        assertEquals(102L, map.get("jobExecutionId"));
    }

    @Test
    void executeBulkJob_HourlyJob_Success() {
        // Given
        SchedulerJobInfo jobInfo = new SchedulerJobInfo();
        jobInfo.setJobName("Channel1Job");
        jobInfo.setJobGroup("profile");
        jobInfo.setJobClass("com.memmcol.hes.jobs.Channel1Job");
        jobInfo.setObisCodes("1.0.99.1.0.255");

        when(schedulerRepository.findByJobNameAndJobGroup("Channel1Job", "profile"))
                .thenReturn(Optional.of(jobInfo));

        BulkJobExecution execution = new BulkJobExecution();
        execution.setId(100L);
        execution.setJobName("Channel1Job");
        execution.setJobGroup("profile");
        execution.setStatus("RUNNING");
        execution.setIntervalType("HOURLY");

        when(bulkJobExecutionRepository.saveAndFlush(any(BulkJobExecution.class)))
                .thenReturn(execution);

        LocalDateTime start = LocalDateTime.of(2025, 10, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2025, 10, 5, 0, 0);
        BulkTriggerRequest request = BulkTriggerRequest.builder()
                .meterSerials(List.of("625250000001", "625250000002"))
                .dateRange(BulkTriggerRequest.DateRange.builder()
                        .startDate(start)
                        .endDate(end)
                        .build())
                .build();

        // When
        Map<String, Object> result = bulkJobExecutionService.executeBulkJob("profile", "Channel1Job", request);

        // Then
        assertNotNull(result);
        assertEquals(100L, result.get("jobExecutionId"));
        assertEquals("RUNNING", result.get("status"));
        assertEquals("HOURLY", result.get("intervalType"));
        assertEquals(2, result.get("totalMeters"));

        verify(bulkJobExecutionRepository).saveAndFlush(any(BulkJobExecution.class));
        verify(bulkJobMeterLogRepository, atLeastOnce()).saveAll(anyList());
    }

    @Test
    void executeBulkJob_DailyJob_Success() {
        SchedulerJobInfo jobInfo = new SchedulerJobInfo();
        jobInfo.setJobName("DailyBillingJob");
        jobInfo.setJobGroup("billing");
        jobInfo.setJobClass("com.memmcol.hes.jobs.DailyBillingJob");
        jobInfo.setObisCodes("0.0.98.2.0.255");

        when(schedulerRepository.findByJobNameAndJobGroup("DailyBillingJob", "billing"))
                .thenReturn(Optional.of(jobInfo));

        BulkJobExecution execution = new BulkJobExecution();
        execution.setId(200L);
        execution.setJobName("DailyBillingJob");
        execution.setJobGroup("billing");
        execution.setStatus("RUNNING");
        execution.setIntervalType("DAILY");

        when(bulkJobExecutionRepository.saveAndFlush(any(BulkJobExecution.class)))
                .thenReturn(execution);

        BulkTriggerRequest request = BulkTriggerRequest.builder()
                .meterSerials(List.of("625250000001"))
                .build();

        Map<String, Object> result = bulkJobExecutionService.executeBulkJob("billing", "DailyBillingJob", request);

        assertNotNull(result);
        assertEquals(200L, result.get("jobExecutionId"));
        assertEquals("DAILY", result.get("intervalType"));
    }

    @Test
    void executeBulkJob_MonthlyJob_Success() {
        SchedulerJobInfo jobInfo = new SchedulerJobInfo();
        jobInfo.setJobName("MonthlyBillingJob");
        jobInfo.setJobGroup("billing");
        jobInfo.setJobClass("com.memmcol.hes.jobs.MonthlyBillingJob");
        jobInfo.setObisCodes("0.0.98.1.0.255");

        when(schedulerRepository.findByJobNameAndJobGroup("MonthlyBillingJob", "billing"))
                .thenReturn(Optional.of(jobInfo));

        BulkJobExecution execution = new BulkJobExecution();
        execution.setId(300L);
        execution.setJobName("MonthlyBillingJob");
        execution.setJobGroup("billing");
        execution.setStatus("RUNNING");
        execution.setIntervalType("MONTHLY");

        when(bulkJobExecutionRepository.saveAndFlush(any(BulkJobExecution.class)))
                .thenReturn(execution);

        BulkTriggerRequest request = BulkTriggerRequest.builder()
                .meterSerials(List.of("625250000001"))
                .build();

        Map<String, Object> result = bulkJobExecutionService.executeBulkJob("billing", "MonthlyBillingJob", request);

        assertNotNull(result);
        assertEquals("MONTHLY", result.get("intervalType"));
    }

    @Test
    void controllerTriggerNow_SingleTrigger_PreservesExistingBehavior() {
        when(quartzJobService.triggerNow("Channel1Job", "profile")).thenReturn(true);

        ResponseEntity<?> response = controller.triggerNow("Channel1Job", "profile", null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("⚡ Job triggered now.", response.getBody());
        verify(quartzJobService).triggerNow("Channel1Job", "profile");
        verifyNoInteractions(bulkJobExecutionRepository);
    }

    @Test
    void controllerTriggerBulkRead_Success() {
        SchedulerJobInfo jobInfo = new SchedulerJobInfo();
        jobInfo.setJobName("Channel1Job");
        jobInfo.setJobGroup("profile");
        jobInfo.setJobClass("com.memmcol.hes.jobs.Channel1Job");

        when(schedulerRepository.findByJobNameAndJobGroup("Channel1Job", "profile"))
                .thenReturn(Optional.of(jobInfo));

        BulkJobExecution execution = new BulkJobExecution();
        execution.setId(101L);
        execution.setJobName("Channel1Job");
        execution.setJobGroup("profile");
        execution.setStatus("RUNNING");
        execution.setIntervalType("HOURLY");

        when(bulkJobExecutionRepository.saveAndFlush(any(BulkJobExecution.class)))
                .thenReturn(execution);

        BulkTriggerRequest request = BulkTriggerRequest.builder()
                .meterSerials(List.of("625250000001"))
                .build();

        ResponseEntity<?> response = controller.triggerBulkRead("Channel1Job", "profile", request);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> map = (Map<?, ?>) response.getBody();
        assertEquals(101L, map.get("jobExecutionId"));

        verifyNoInteractions(quartzJobService);
    }

    @Test
    void controllerTriggerNow_BulkTrigger_DelegatesToService() {
        SchedulerJobInfo jobInfo = new SchedulerJobInfo();
        jobInfo.setJobName("Channel1Job");
        jobInfo.setJobGroup("profile");
        jobInfo.setJobClass("com.memmcol.hes.jobs.Channel1Job");

        when(schedulerRepository.findByJobNameAndJobGroup("Channel1Job", "profile"))
                .thenReturn(Optional.of(jobInfo));

        BulkJobExecution execution = new BulkJobExecution();
        execution.setId(101L);
        execution.setJobName("Channel1Job");
        execution.setJobGroup("profile");
        execution.setStatus("RUNNING");
        execution.setIntervalType("HOURLY");

        when(bulkJobExecutionRepository.saveAndFlush(any(BulkJobExecution.class)))
                .thenReturn(execution);

        BulkTriggerRequest request = BulkTriggerRequest.builder()
                .meterSerials(List.of("625250000001"))
                .build();

        ResponseEntity<?> response = controller.triggerNow("Channel1Job", "profile", request);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> map = (Map<?, ?>) response.getBody();
        assertEquals(101L, map.get("jobExecutionId"));

        verifyNoInteractions(quartzJobService);
    }
}
