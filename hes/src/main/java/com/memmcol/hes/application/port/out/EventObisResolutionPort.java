package com.memmcol.hes.application.port.out;

import com.memmcol.hes.domain.events.EventScheduleProfile;
import com.memmcol.hes.domain.events.ResolvedTieredEventObis;

/**
 * Resolves OBIS codes for category-aware event scheduling (e.g. household vs MD/CT token events).
 * Implementations may read from configuration, database, or job overrides.
 */
public interface EventObisResolutionPort {

    /**
     * @param profile              logical event profile (maps to configuration)
     * @param schedulerMdCtObis    primary OBIS from the scheduler job ({@code obisCodes} in JobDataMap / DB)
     * @param schedulerHouseholdObis optional override for household tier (JobDataMap {@code obisCodesHousehold})
     */
    ResolvedTieredEventObis resolve(EventScheduleProfile profile,
                                    String schedulerMdCtObis,
                                    String schedulerHouseholdObis);
}
