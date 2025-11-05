package com.memmcol.hes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = {
        "com.memmcol.hes",
        "com.memmcol.hesTraining"
})
@EnableScheduling
@EnableCaching
@EnableDiscoveryClient
public class HesApplication {

    public static void main(String[] args) {
        SpringApplication.run(HesApplication.class, args);
    }


}
