package com.memmcol.hes.config;

import java.util.Properties;

import javax.sql.DataSource;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.quartz.QuartzProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

@Slf4j
public class SchedulerConfig {

    private final DataSource dataSource;
    private final ApplicationContext applicationContext;
    private final QuartzProperties quartzProperties;

    public SchedulerConfig(DataSource dataSource,
                           ApplicationContext applicationContext,
                           QuartzProperties quartzProperties) {
        this.dataSource = dataSource;
        this.applicationContext = applicationContext;
        this.quartzProperties = quartzProperties;
    }


    @Bean
    public SchedulerFactoryBean schedulerFactoryBean() {
        SchedulerJobFactory jobFactory = new SchedulerJobFactory();
        jobFactory.setApplicationContext(applicationContext);

        Properties properties = new Properties();
        properties.putAll(quartzProperties.getProperties());

        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setOverwriteExistingJobs(true);
        factory.setDataSource(dataSource);   // ✅ DataSource injected properly now
        factory.setQuartzProperties(properties);
        factory.setJobFactory(jobFactory);

        log.info("✅ SchedulerFactoryBean created successfully");

        return factory;
    }
}
