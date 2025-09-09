package com.memmcol.hes.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class SchedulerDataSourceConfig {
    @Bean(name = "dataSourceConfig")
    @Primary
    public DataSource dataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:postgresql://172.16.10.28:8324/gridflex-DB");
        dataSource.setUsername("postgres");
        dataSource.setPassword("Passw0rd0405&");
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setMaximumPoolSize(20);
        dataSource.setMinimumIdle(5);
        dataSource.setIdleTimeout(30000);
        dataSource.setConnectionTimeout(30000);
        dataSource.setMaxLifetime(600000);
        dataSource.setPoolName("HikariPool-HES");
        return dataSource;
    }
}
