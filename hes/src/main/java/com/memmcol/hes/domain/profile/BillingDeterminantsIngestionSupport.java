package com.memmcol.hes.domain.profile;

import com.memmcol.hes.application.port.out.ProfileStatePort;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Shared incremental ingestion for monthly billing determinants (MD/CT and household -hh tiers).
 * <p>
 * Decouples extraction from rigid calendar-month batch boundaries by using configurable
 * read windows and cursor-driven catch-up. {@code meter_profile_state.last_timestamp} is
 * advanced only from meter-derived row timestamps (never system clock alone).
 */
public final class BillingDeterminantsIngestionSupport {

    private BillingDeterminantsIngestionSupport() {
    }

    public static LocalDateTime resolveSeedTimestamp(ProfileStatePort statePort,
                                                     String meterSerial,
                                                     String profileObis,
                                                     LocalDateTime meterCreatedAt) {
        ProfileState st = statePort.loadState(meterSerial, profileObis);
        if (st != null && st.lastTimestamp() != null) {
            return st.lastTimestamp().value();
        }
        if (meterCreatedAt != null) {
            return meterCreatedAt;
        }
        return LocalDateTime.now()
                .minusMonths(1)
                .withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
    }

    public static LocalDateTime effectiveSeedFrom(LocalDateTime seedFrom,
                                                  BillingCycleGracePeriodPolicy gracePolicy) {
        if (gracePolicy == null) {
            return seedFrom;
        }
        return effectiveSeedFrom(seedFrom, gracePolicy, LocalDate.now(gracePolicy.zone()));
    }

    /**
     * During the billing-cycle grace window, ensure we re-scan from the prior month boundary
     * so delayed cycle-close reads are captured even if the cursor had already advanced.
     */
    public static LocalDateTime effectiveSeedFrom(LocalDateTime seedFrom,
                                                  BillingCycleGracePeriodPolicy gracePolicy,
                                                  LocalDate referenceDate) {
        if (gracePolicy == null || !gracePolicy.isWithinGracePeriod(referenceDate)) {
            return seedFrom;
        }
        LocalDate lookback = gracePolicy.cycleCloseLookbackStart(referenceDate);
        LocalDateTime graceFloor = lookback.atStartOfDay();
        if (seedFrom == null || seedFrom.isAfter(graceFloor)) {
            return graceFloor;
        }
        return seedFrom;
    }

    /**
     * Computes the next DLMS read window end using a sliding day window (not full calendar month).
     */
    public static LocalDateTime computeWindowEnd(LocalDateTime from, LocalDateTime now, int readWindowDays) {
        int days = Math.max(1, readWindowDays);
        LocalDateTime to = from.plusDays(days);
        return to.isAfter(now) ? now : to;
    }

    public static ProfileTimestamp advanceInMemoryCursor(LocalDateTime windowEnd) {
        return new ProfileTimestamp(windowEnd);
    }

    public static ProfileTimestamp nextCursorAfterBatch(ProfileSyncResult syncResult, LocalDateTime windowEnd) {
        ProfileTimestamp resume = ProfileTimestamp.ofNullable(syncResult.getAdvanceTo());
        return resume != null ? resume : new ProfileTimestamp(windowEnd);
    }

    public static boolean shouldBreakOnEmptyWithException(boolean exceptionOccurred,
                                                          java.util.List<?> rawRows) {
        return exceptionOccurred && (rawRows == null || rawRows.isEmpty());
    }

    public static boolean isEmptyWindow(java.util.List<?> rawRows) {
        return rawRows == null || rawRows.isEmpty();
    }
}
