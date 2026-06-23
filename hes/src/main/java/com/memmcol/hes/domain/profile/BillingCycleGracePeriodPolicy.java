package com.memmcol.hes.domain.profile;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Configurable billing-cycle close grace window (default days 1–5 of each month).
 * During this window, scheduled re-runs safely upsert delayed meter pushes without
 * duplicating determinants.
 */
@Component
public class BillingCycleGracePeriodPolicy {

    private final int gracePeriodDays;
    private final ZoneId zone;

    public BillingCycleGracePeriodPolicy(
            @Value("${hes.billing.determinants.grace-period.days:5}") int gracePeriodDays,
            @Value("${hes.profile.execution.window.zone:Africa/Lagos}") String zoneId) {
        this.gracePeriodDays = Math.max(3, Math.min(5, gracePeriodDays));
        this.zone = ZoneId.of(zoneId);
    }

    public int gracePeriodDays() {
        return gracePeriodDays;
    }

    public ZoneId zone() {
        return zone;
    }

    public boolean isWithinGracePeriod(LocalDate date) {
        return date.getDayOfMonth() >= 1 && date.getDayOfMonth() <= gracePeriodDays;
    }

    public boolean isWithinGracePeriodNow() {
        return isWithinGracePeriod(LocalDate.now(zone));
    }

    /**
     * Lower bound for re-reading cycle-close determinants during the grace window.
     * Returns the first instant of the previous calendar month.
     */
    public LocalDate cycleCloseLookbackStart(LocalDate today) {
        return today.withDayOfMonth(1).minusMonths(1);
    }
}
