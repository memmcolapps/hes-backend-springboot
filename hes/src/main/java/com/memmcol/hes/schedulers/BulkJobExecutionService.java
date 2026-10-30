package com.memmcol.hes.schedulers;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkJobExecutionService {

    private final SchedulerRepository schedulerRepository;
    private final MeterRepository meterRepository;
    private final BulkJobExecutionRepository bulkJobExecutionRepository;
    private final BulkJobMeterLogRepository bulkJobMeterLogRepository;
    private final MetersLockService metersLockService;
    private final Scheduler scheduler;

    public Map<String, Object> executeBulkJob(String jobGroup, String jobName, BulkTriggerRequest request) {
        // 1. Fetch job definition from DB or Quartz
        Optional<SchedulerJobInfo> jobInfoOpt = schedulerRepository.findByJobNameAndJobGroup(jobName, jobGroup);
        String jobClass = jobInfoOpt.map(SchedulerJobInfo::getJobClass).orElse(null);
        String obisCodes = jobInfoOpt.map(SchedulerJobInfo::getObisCodes).orElse("");
        String obisCodesHousehold = jobInfoOpt.map(SchedulerJobInfo::getObisCodesHousehold).orElse("");

        if (jobClass == null || jobClass.isBlank()) {
            try {
                JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
                if (scheduler.checkExists(jobKey)) {
                    JobDetail detail = scheduler.getJobDetail(jobKey);
                    jobClass = detail.getJobClass().getName();
                    JobDataMap dataMap = detail.getJobDataMap();
                    if (dataMap.containsKey("obisCodes")) {
                        obisCodes = dataMap.getString("obisCodes");
                    }
                    if (dataMap.containsKey("obisCodesHousehold")) {
                        obisCodesHousehold = dataMap.getString("obisCodesHousehold");
                    }
                }
            } catch (Exception e) {
                log.warn("Could not retrieve Quartz JobDetail for {}/{}", jobGroup, jobName, e);
            }
        }

        // 2. Resolve dynamic interval type (HOURLY, DAILY, MONTHLY)
        String intervalType = resolveIntervalType(jobName, jobGroup, jobClass);

        // 3. Resolve requested meter serials
        List<String> requestedSerials = request != null && request.getMeterSerials() != null
                ? request.getMeterSerials().stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList()
                : Collections.emptyList();

        List<String> targetSerials;
        if (requestedSerials.isEmpty()) {
            targetSerials = meterRepository.findAllRegisteredMeterNumbers();
        } else {
            targetSerials = requestedSerials;
        }

        if (targetSerials.isEmpty()) {
            throw new IllegalArgumentException("No valid target meters found for bulk job execution.");
        }

        // 4. Resolve date range parameters
        LocalDateTime startDate = request != null && request.getDateRange() != null ? request.getDateRange().getStartDate() : null;
        LocalDateTime endDate = request != null && request.getDateRange() != null ? request.getDateRange().getEndDate() : null;

        // 5. Create BulkJobExecution entity
        BulkJobExecution execution = new BulkJobExecution();
        execution.setJobName(jobName);
        execution.setJobGroup(jobGroup);
        execution.setStatus("RUNNING");
        execution.setTotalMeters(targetSerials.size());
        execution.setIntervalType(intervalType);
        execution.setStartDate(startDate);
        execution.setEndDate(endDate);
        execution.setCreatedAt(LocalDateTime.now());
        execution.setStartedAt(LocalDateTime.now());
        BulkJobExecution savedExecution = bulkJobExecutionRepository.saveAndFlush(execution);

        Long executionId = savedExecution.getId();

        // 6. Pre-create meter log records in optimized batches
        List<BulkJobMeterLog> logEntries = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (String serial : targetSerials) {
            BulkJobMeterLog logEntry = new BulkJobMeterLog();
            logEntry.setJobExecutionId(executionId);
            logEntry.setMeterNumber(serial);
            logEntry.setStatus("PENDING");
            logEntry.setAttemptCount(0);
            logEntry.setCreatedAt(now);
            logEntry.setUpdatedAt(now);
            logEntries.add(logEntry);
        }

        saveMeterLogsInBatches(logEntries, 500);

        // 7. Trigger sequential background worker processing
        final String finalJobClass = jobClass;
        final String finalObisCodes = obisCodes;
        final String finalObisHousehold = obisCodesHousehold;

        CompletableFuture.runAsync(() -> processSequentialBulkJob(
                savedExecution,
                targetSerials,
                finalJobClass,
                finalObisCodes,
                finalObisHousehold
        ));

        // 8. Build immediate start response
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jobExecutionId", executionId);
        response.put("jobName", jobName);
        response.put("jobGroup", jobGroup);
        response.put("status", "RUNNING");
        response.put("totalMeters", targetSerials.size());
        response.put("intervalType", intervalType);
        response.put("startDate", startDate);
        response.put("endDate", endDate);
        response.put("message", "Bulk job execution triggered successfully.");

        return response;
    }

    private void saveMeterLogsInBatches(List<BulkJobMeterLog> logEntries, int chunkSize) {
        for (int i = 0; i < logEntries.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, logEntries.size());
            bulkJobMeterLogRepository.saveAll(logEntries.subList(i, end));
            bulkJobMeterLogRepository.flush();
        }
    }

    private String resolveIntervalType(String jobName, String jobGroup, String jobClass) {
        String combined = (jobName + " " + jobGroup + " " + (jobClass != null ? jobClass : "")).toLowerCase();
        if (combined.contains("monthly")) {
            return "MONTHLY";
        }
        if (combined.contains("daily") || combined.contains("billing") || combined.contains("event")) {
            return "DAILY";
        }
        if (combined.contains("hourly") || combined.contains("channel") || combined.contains("profile")) {
            return "HOURLY";
        }
        return "DAILY";
    }

    public Map<String, Object> getBulkExecutionStatus(Long executionId) {
        BulkJobExecution execution = bulkJobExecutionRepository.findById(executionId)
                .orElseThrow(() -> new NoSuchElementException("Bulk job execution not found: " + executionId));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("executionId", execution.getId());
        response.put("jobName", execution.getJobName());
        response.put("jobGroup", execution.getJobGroup());
        response.put("status", execution.getStatus());
        response.put("intervalType", execution.getIntervalType());
        response.put("totalMeters", execution.getTotalMeters());
        response.put("successful", bulkJobMeterLogRepository.countByJobExecutionIdAndStatus(executionId, "SUCCESS"));
        response.put("failed", bulkJobMeterLogRepository.countByJobExecutionIdAndStatus(executionId, "FAILED"));
        response.put("pending", bulkJobMeterLogRepository.countByJobExecutionIdAndStatus(executionId, "PENDING"));
        response.put("processing", bulkJobMeterLogRepository.countByJobExecutionIdAndStatus(executionId, "PROCESSING"));
        response.put("startDate", execution.getStartDate());
        response.put("endDate", execution.getEndDate());
        response.put("createdAt", execution.getCreatedAt());
        response.put("startedAt", execution.getStartedAt());
        response.put("completedAt", execution.getCompletedAt());
        response.put("errorMessage", execution.getErrorMessage());

        return response;
    }

    private void processSequentialBulkJob(
            BulkJobExecution execution,
            List<String> targetSerials,
            String jobClass,
            String obisCodes,
            String obisCodesHousehold
    ) {
        Long executionId = execution.getId();
        log.info("Starting sequential bulk execution ID={} for job {}/{} across {} meters",
                executionId, execution.getJobGroup(), execution.getJobName(), targetSerials.size());

        // Fetch meter details in chunks of 500 to avoid SQL IN clause limits
        Map<String, MeterDTO> meterDetailsMap = new HashMap<>();
        int chunkSize = 500;
        for (int i = 0; i < targetSerials.size(); i += chunkSize) {
            List<String> subList = targetSerials.subList(i, Math.min(i + chunkSize, targetSerials.size()));
            List<MeterDTO> dtos = meterRepository.findMeterDetailsByMeterNumberIn(subList);
            for (MeterDTO dto : dtos) {
                dto.determineMD();
                meterDetailsMap.put(dto.getMeterNumber(), dto);
            }
        }

        List<BulkJobMeterLog> pendingLogs = bulkJobMeterLogRepository.findByJobExecutionIdAndStatus(executionId, "PENDING");
        Map<String, BulkJobMeterLog> logMap = new HashMap<>();
        for (BulkJobMeterLog meterLog : pendingLogs) {
            logMap.put(meterLog.getMeterNumber(), meterLog);
        }

        for (String meterSerial : targetSerials) {
            BulkJobMeterLog meterLog = logMap.get(meterSerial);
            if (meterLog == null) {
                meterLog = new BulkJobMeterLog();
                meterLog.setJobExecutionId(executionId);
                meterLog.setMeterNumber(meterSerial);
                meterLog.setStatus("PENDING");
                meterLog.setCreatedAt(LocalDateTime.now());
            }

            meterLog.setStatus("PROCESSING");
            meterLog.setAttemptCount(meterLog.getAttemptCount() + 1);
            meterLog.setUpdatedAt(LocalDateTime.now());
            bulkJobMeterLogRepository.save(meterLog);

            long startTime = System.currentTimeMillis();
            try {
                MeterDTO meterDTO = meterDetailsMap.get(meterSerial);
                if (meterDTO == null) {
                    meterDTO = new MeterDTO();
                    meterDTO.setMeterNumber(meterSerial);
                    meterDTO.setMeterModel("GENERIC");
                    meterDTO.determineMD();
                }

                executeCoreJobForMeter(
                        execution.getJobName(),
                        execution.getJobGroup(),
                        jobClass,
                        obisCodes,
                        obisCodesHousehold,
                        meterDTO
                );

                long duration = System.currentTimeMillis() - startTime;
                meterLog.setStatus("SUCCESS");
                meterLog.setProcessedAt(LocalDateTime.now());
                meterLog.setDurationMs(duration);
                meterLog.setUpdatedAt(LocalDateTime.now());
                bulkJobMeterLogRepository.save(meterLog);

            } catch (Exception ex) {
                long duration = System.currentTimeMillis() - startTime;
                log.error("Bulk job execution failed for meter {} on job {}/{}",
                        meterSerial, execution.getJobGroup(), execution.getJobName(), ex);

                meterLog.setStatus("FAILED");
                meterLog.setLastError(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getName());
                meterLog.setProcessedAt(LocalDateTime.now());
                meterLog.setDurationMs(duration);
                meterLog.setUpdatedAt(LocalDateTime.now());
                bulkJobMeterLogRepository.save(meterLog);
            }
        }

        execution.setStatus("COMPLETED");
        execution.setCompletedAt(LocalDateTime.now());
        bulkJobExecutionRepository.save(execution);
        log.info("Completed sequential bulk execution ID={} for job {}/{}",
                executionId, execution.getJobGroup(), execution.getJobName());
    }

    private void executeCoreJobForMeter(
            String jobName,
            String jobGroup,
            String jobClass,
            String obisCodes,
            String obisCodesHousehold,
            MeterDTO meter
    ) {
        String clazz = jobClass != null ? jobClass : "";
        String name = jobName != null ? jobName : "";

        String model = meter.getMeterModel();
        String serial = meter.getMeterNumber();
        boolean isMD = meter.isMD();

        String hhObis = (obisCodesHousehold != null && !obisCodesHousehold.isBlank()) ? obisCodesHousehold : obisCodes;

        if (clazz.contains("Channel1JobHouseHold") || name.contains("Channel1JobHouseHold")) {
            metersLockService.readChannelOneHouseholdWithLock(model, serial, hhObis, isMD);
        } else if (clazz.contains("Channel1Job") || name.contains("Channel1Job")) {
            metersLockService.readChannelOneWithLock(model, serial, obisCodes, isMD);
        } else if (clazz.contains("Channel2JobHouseHold") || name.contains("Channel2JobHouseHold")) {
            metersLockService.readChannelTwoHouseholdWithLock(model, serial, hhObis, isMD);
        } else if (clazz.contains("Channel2Job") || name.contains("Channel2Job")) {
            metersLockService.readChannelTwoWithLock(model, serial, obisCodes, isMD);
        } else if (clazz.contains("Channel3JobHouseHold") || name.contains("Channel3JobHouseHold")) {
            metersLockService.readChannelThreeHouseholdWithLock(model, serial, hhObis, isMD);
        } else if (clazz.contains("DailyBillingDataHouseHoldJob") || name.contains("DailyBillingDataHouseHoldJob")) {
            metersLockService.readDailyBillingDataHouseholdWithLock(model, serial, hhObis, isMD);
        } else if (clazz.contains("DailyBillingEnergyHouseHoldJob") || name.contains("DailyBillingEnergyHouseHoldJob")) {
            metersLockService.readDailyBillingEnergyHouseholdWithLock(model, serial, hhObis, isMD);
        } else if (clazz.contains("DailyBillingJob") || name.contains("DailyBillingJob")) {
            metersLockService.readDailyBillWithLock(model, serial, obisCodes, isMD);
        } else if (clazz.contains("MonthlyBillingDataHouseHoldJob") || name.contains("MonthlyBillingDataHouseHoldJob")) {
            metersLockService.readMonthlyBillingDataHouseholdWithLock(model, serial, hhObis, isMD);
        } else if (clazz.contains("MonthlyBillingEnergyHouseHoldJob") || name.contains("MonthlyBillingEnergyHouseHoldJob")) {
            metersLockService.readMonthlyBillingEnergyHouseholdWithLock(model, serial, hhObis, isMD);
        } else if (clazz.contains("MonthlyBillingJob") || name.contains("MonthlyBillingJob")) {
            metersLockService.readMonthlyBillWithLock(model, serial, obisCodes, isMD);
        } else if (clazz.contains("RechargeTokenEventJob") || name.contains("RechargeTokenEventJob")) {
            metersLockService.readHouseholdRechargeTokenEventsWithLock(model, serial, hhObis, isMD);
        } else if (clazz.contains("ManageTokenEventJob") || name.contains("ManageTokenEventJob")) {
            metersLockService.readHouseholdManagementTokenEventsWithLock(model, serial, hhObis, isMD);
        } else if (clazz.contains("FraudEventJob") || name.contains("FraudEventJob")) {
            metersLockService.readHouseholdFraudEventsWithLock(model, serial, hhObis, isMD);
        } else if (clazz.contains("ControlEventJob") || name.contains("ControlEventJob")) {
            metersLockService.readHouseholdControlEventsWithLock(model, serial, hhObis, isMD);
        } else if (clazz.contains("StandardEventJob") || clazz.contains("PowerGridEventJob") || name.contains("Event")) {
            metersLockService.readEventsWithLock(model, serial, obisCodes, isMD);
        } else {
            metersLockService.readChannelOneWithLock(model, serial, obisCodes, isMD);
        }
    }
}
