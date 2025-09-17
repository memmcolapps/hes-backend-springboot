package com.memmcol.hes.csv;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

//@Component
public class CsvDataLoader implements CommandLineRunner {

    private final EventCodeLoaderService loaderService;

    public CsvDataLoader(EventCodeLoaderService loaderService) {
        this.loaderService = loaderService;
    }

    @Override
    public void run(String... args) throws Exception {
        loaderService.loadCsv("src/main/resources/event_codes.csv");
    }
}
