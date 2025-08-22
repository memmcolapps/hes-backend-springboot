package com.memmcol.hes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableCaching
public class HesApplication {

    public static void main(String[] args) {
        SpringApplication.run(HesApplication.class, args);
    }


}
