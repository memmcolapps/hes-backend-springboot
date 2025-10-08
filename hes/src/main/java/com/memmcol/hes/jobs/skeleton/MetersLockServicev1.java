package com.memmcol.hes.jobs.skeleton;

import org.springframework.stereotype.Service;

@Service
public class MetersLockServicev1 {
    public void readChannelOneWithLock(String model, String meter, String profileObis, int timeout) {
        System.out.println("🔒 Reading Channel1 for meter " + meter);
    }
}


