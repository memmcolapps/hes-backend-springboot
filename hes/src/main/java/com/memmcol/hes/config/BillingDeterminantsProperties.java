package com.memmcol.hes.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class BillingDeterminantsProperties {

    private final int readWindowDays;
    private final boolean continuousIngestionEnabled;

    public BillingDeterminantsProperties(
            @Value("${hes.billing.determinants.read-window-days:7}") int readWindowDays,
            @Value("${hes.billing.determinants.continuous.enabled:true}") boolean continuousIngestionEnabled) {
        this.readWindowDays = Math.max(1, readWindowDays);
        this.continuousIngestionEnabled = continuousIngestionEnabled;
    }
}
