package com.memmcol.hes.jobs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.memmcol.hes.model.SchedulerJobInfo;
import com.memmcol.hes.schedulers.QuartzJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Slf4j
@Component
public class DefaultJobInitializer {

    private final QuartzJobService quartzJobService;

    public DefaultJobInitializer(QuartzJobService quartzJobService) {
        this.quartzJobService = quartzJobService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeDefaultJobs() {
        try {
            // Load default jobs from JSON
            ObjectMapper mapper = new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .enable(SerializationFeature.INDENT_OUTPUT);
            InputStream inputStream = new ClassPathResource("default-jobs.json").getInputStream();
            List<SchedulerJobInfo> defaultJobs = mapper.readValue(inputStream, new TypeReference<List<SchedulerJobInfo>>() {});

            for (SchedulerJobInfo job : defaultJobs) {
                try {
                    quartzJobService.saveIfNew(job);
                } catch (Exception e) {
                    log.error("Failed to insert/update job [{}]: {}", job.getJobName(), e.getMessage());
                }
            }

            log.info("✅ Default Quartz jobs loaded successfully.");
        } catch (Exception e) {
            log.error("❌ Failed to load default jobs: {}", e.getMessage(), e);
        }
    }
}