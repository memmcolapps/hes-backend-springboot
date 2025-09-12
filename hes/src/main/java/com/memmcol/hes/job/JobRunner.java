package com.memmcol.hes.job;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class JobRunner {

    private final LoadProfileChannel1Job loadProfileChannel1Job;

    @Autowired
    public JobRunner(LoadProfileChannel1Job loadProfileChannel1Job) {
        this.loadProfileChannel1Job = loadProfileChannel1Job;
    }

    public void runNow() {
        log.info("Manually triggering LoadProfileChannel1Job...");
//        loadProfileChannel1Job.executeProfile(null);
        loadProfileChannel1Job.executeProfileTestMode(null);
    }
}
