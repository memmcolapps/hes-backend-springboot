package com.memmcol.hes.domain.profile;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class BillingDeterminantsIngestionSupportTest {

    private static final ZoneId LAGOS = ZoneId.of("Africa/Lagos");

    @Test
    void computeWindowEnd_clampsToNow() {
        LocalDateTime from = LocalDateTime.of(2026, 3, 1, 0, 0);
        LocalDateTime now = LocalDateTime.of(2026, 3, 5, 12, 0);
        LocalDateTime to = BillingDeterminantsIngestionSupport.computeWindowEnd(from, now, 7);
        assertEquals(now, to);
    }

    @Test
    void computeWindowEnd_usesConfiguredDays() {
        LocalDateTime from = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime now = LocalDateTime.of(2026, 6, 1, 0, 0);
        LocalDateTime to = BillingDeterminantsIngestionSupport.computeWindowEnd(from, now, 7);
        assertEquals(from.plusDays(7), to);
    }

    @Test
    void effectiveSeedFrom_pullsBackDuringGracePeriod() {
        BillingCycleGracePeriodPolicy policy = new BillingCycleGracePeriodPolicy(5, "Africa/Lagos");
        LocalDateTime seedFrom = LocalDateTime.of(2026, 2, 15, 0, 0);
        LocalDate today = LocalDate.of(2026, 3, 3);

        assertTrue(policy.isWithinGracePeriod(today));

        LocalDateTime effective = BillingDeterminantsIngestionSupport.effectiveSeedFrom(seedFrom, policy, today);
        assertEquals(LocalDateTime.of(2026, 2, 1, 0, 0), effective);
    }

    @Test
    void effectiveSeedFrom_keepsEarlierSeedWhenAlreadyBeforeGraceFloor() {
        BillingCycleGracePeriodPolicy policy = new BillingCycleGracePeriodPolicy(5, "Africa/Lagos");
        LocalDateTime seedFrom = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime effective = BillingDeterminantsIngestionSupport.effectiveSeedFrom(
                seedFrom, policy, LocalDate.of(2026, 3, 3));
        assertEquals(seedFrom, effective);
    }

    @Test
    void gracePeriodDays_clampedToThreeThroughFive() {
        assertEquals(3, new BillingCycleGracePeriodPolicy(1, "Africa/Lagos").gracePeriodDays());
        assertEquals(5, new BillingCycleGracePeriodPolicy(10, "Africa/Lagos").gracePeriodDays());
        assertEquals(4, new BillingCycleGracePeriodPolicy(4, "Africa/Lagos").gracePeriodDays());
    }
}
