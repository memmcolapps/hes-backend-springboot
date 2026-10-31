package com.memmcol.hes.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Ensures bulk_job_execution and bulk_job_meter_log tables exist in the database
 * BEFORE Hibernate's entityManagerFactory validates the schema (spring.jpa.hibernate.ddl-auto=validate).
 */
@Slf4j
@Component
public class BulkJobSchemaInitializer implements BeanFactoryPostProcessor, PriorityOrdered, EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (environment == null) {
            return;
        }

        String url = environment.getProperty("spring.datasource.url");
        String username = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");
        String driver = environment.getProperty("spring.datasource.driver-class-name");

        if (url == null || url.isBlank()) {
            return;
        }

        try {
            if (driver != null && !driver.isBlank()) {
                Class.forName(driver);
            }
            try (Connection conn = DriverManager.getConnection(url, username, password);
                 Statement stmt = conn.createStatement()) {

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS bulk_job_execution (
                        id                  BIGSERIAL PRIMARY KEY,
                        job_name            VARCHAR(255)  NOT NULL,
                        job_group           VARCHAR(255)  NOT NULL,
                        status              VARCHAR(30)   NOT NULL DEFAULT 'PENDING',
                        total_meters        INT           NOT NULL DEFAULT 0,
                        interval_type       VARCHAR(20)   NULL,
                        start_date          TIMESTAMP     NULL,
                        end_date            TIMESTAMP     NULL,
                        created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        started_at          TIMESTAMP     NULL,
                        completed_at        TIMESTAMP     NULL,
                        error_message       TEXT          NULL
                    );
                """);

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS bulk_job_meter_log (
                        id                  BIGSERIAL PRIMARY KEY,
                        job_execution_id    BIGINT        NOT NULL REFERENCES bulk_job_execution(id) ON DELETE CASCADE,
                        meter_number        VARCHAR(100)  NOT NULL,
                        status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
                        attempt_count       INT           NOT NULL DEFAULT 0,
                        last_error          TEXT          NULL,
                        created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        processed_at        TIMESTAMP     NULL,
                        duration_ms         BIGINT        NULL,
                        tx_frames           TEXT          NULL,
                        rx_frames           TEXT          NULL
                    );
                """);

                stmt.execute("ALTER TABLE bulk_job_meter_log ADD COLUMN IF NOT EXISTS tx_frames TEXT;");
                stmt.execute("ALTER TABLE bulk_job_meter_log ADD COLUMN IF NOT EXISTS rx_frames TEXT;");

                log.info("Pre-JPA schema check: bulk_job_execution and bulk_job_meter_log tables verified/created.");
            }
        } catch (Exception e) {
            log.warn("Pre-JPA schema initialization check completed: {}", e.getMessage());
        }
    }
}
