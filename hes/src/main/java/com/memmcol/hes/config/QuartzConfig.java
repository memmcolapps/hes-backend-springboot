package com.memmcol.hes.config;

import com.memmcol.hes.schedulers.JobStatusListener;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.PropertiesFactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.Objects;

@Configuration
@AllArgsConstructor
@Slf4j
public class QuartzConfig {

    private final JobStatusListener jobStatusListener;

    @Bean
    public SchedulerFactoryBean schedulerFactoryBean(
            DataSource dataSource,
            ApplicationContext applicationContext,
            Environment environment) throws IOException {

        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setApplicationContext(applicationContext);
        factory.setGlobalJobListeners(jobStatusListener);

        // ✅ Wire Spring's DI into Quartz jobs
        AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory();
        jobFactory.setApplicationContext(applicationContext);
        factory.setJobFactory(jobFactory);

        // ✅ Load profile-specific quartz.properties (quartz-dev.properties, quartz-prod.properties, etc.)
        String[] activeProfiles = environment.getActiveProfiles();
        String activeProfile = (activeProfiles.length > 0) ? activeProfiles[0] : "default";
        String quartzFile = "quartz-" + activeProfile + ".properties";

        PropertiesFactoryBean propertiesFactoryBean = new PropertiesFactoryBean();
        propertiesFactoryBean.setLocation(new ClassPathResource(quartzFile));
        propertiesFactoryBean.afterPropertiesSet();
        factory.setQuartzProperties(Objects.requireNonNull(propertiesFactoryBean.getObject()));

        return factory;
    }

    // Expose Scheduler so others get the same instance
    @Bean
    public Scheduler scheduler(SchedulerFactoryBean factory) throws SchedulerException {
        return factory.getScheduler();
    }

    /**
     * Helper that lets Quartz jobs get Spring-managed beans.
     */
    private static final class AutowiringSpringBeanJobFactory
            extends SpringBeanJobFactory implements ApplicationContextAware {

        private transient AutowireCapableBeanFactory beanFactory;

        @Override
        public void setApplicationContext(ApplicationContext context) {
            this.beanFactory = context.getAutowireCapableBeanFactory();
        }

        @Override
        protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
            Object job = super.createJobInstance(bundle);
            log.debug("➡ Autowiring {}", job.getClass().getName());
            beanFactory.autowireBean(job); // inject @Autowired / constructor deps
            return job;
        }
    }
}
