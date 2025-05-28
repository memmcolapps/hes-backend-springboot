package com.memmcol.hes;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HesApplication {

    public static void main(String[] args) {
        SpringApplication.run(HesApplication.class, args);
    }


}
