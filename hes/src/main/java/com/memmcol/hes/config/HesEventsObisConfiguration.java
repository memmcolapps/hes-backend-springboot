package com.memmcol.hes.config;

import com.memmcol.hes.config.properties.HesEventObisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(HesEventObisProperties.class)
public class HesEventsObisConfiguration {
}
