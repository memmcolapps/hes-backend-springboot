package com.memmcol.hes.config;

import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

public class SchedulerJobFactory extends SpringBeanJobFactory implements ApplicationContextAware {

    private AutowireCapableBeanFactory beanFactory;

    @Override
    public void setApplicationContext(final ApplicationContext context) {
        if (context == null) {
            throw new IllegalArgumentException("ApplicationContext cannot be null");
        }
        beanFactory = context.getAutowireCapableBeanFactory();
    }

    @Override
    protected Object createJobInstance(final TriggerFiredBundle bundle) throws Exception {
        try {
            final Object job = super.createJobInstance(bundle);
            beanFactory.autowireBean(job);
            return job;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create and autowire job instance for " + bundle.getJobDetail().getKey(), e);
        }
    }
}