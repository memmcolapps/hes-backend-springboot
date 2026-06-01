package com.memmcol.hes.domain.profile;

import com.memmcol.hes.application.port.out.ProfileStatePort;

import java.time.LocalDateTime;
import java.util.function.Predicate;

/**
 * Shared day-window ingestion helpers aligned with {@link DailyBillingDataHouseholdService}.
 * <p>
 * {@code meter_profile_state.last_timestamp} must only be updated from timestamps present on
 * meter profile rows (via persistence adapters). Use {@link #advanceInMemoryCursor} for gap
 * skipping within a single job run — never persist cursor or system time as {@code last_timestamp}.
 * <p>
 * For event-type profiles, this rule may be relaxed to "jump" gaps when no data is returned.
 */
public final class HouseholdDayWindowIngestionSupport {

    private HouseholdDayWindowIngestionSupport() {
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
        return LocalDateTime.now().minusDays(1);
    }

    /**
     * In-memory-only cursor step for empty or unmappable windows. Does not touch profile state.
     */
    public static ProfileTimestamp advanceInMemoryCursor(LocalDateTime windowEnd) {
        return new ProfileTimestamp(windowEnd);
    }

    /**
     * Next in-memory cursor after a successful persist batch — uses meter-derived {@code advanceTo} only.
     */
    public static ProfileTimestamp nextCursorAfterBatch(ProfileSyncResult syncResult) {
        return ProfileTimestamp.ofNullable(syncResult.getAdvanceTo());
    }

    /**
     * Jumps the persistence state to the end of the window. Use with caution for events only.
     */
    public static void jumpAndPersistState(ProfileStatePort statePort,
                                           String meterSerial,
                                           String profileObis,
                                           LocalDateTime windowEnd) {
        statePort.upsertState(meterSerial, profileObis, new ProfileTimestamp(windowEnd), new CapturePeriod(1));
    }

    /**
     * Stops infinite loops when the meter returns rows but mapping yields no persistable records.
     */
    public static <T> boolean shouldSkipUnmappableBatch(java.util.List<T> dtos, Predicate<T> hasRequiredFields) {
        if (dtos == null || dtos.isEmpty()) {
            return true;
        }
        return dtos.stream().noneMatch(hasRequiredFields);
    }
}
