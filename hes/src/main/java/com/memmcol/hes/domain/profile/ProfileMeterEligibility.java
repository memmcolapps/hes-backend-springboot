package com.memmcol.hes.domain.profile;

import com.memmcol.hes.dto.MeterDTO;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Filters which meters participate in scheduled <em>profile and billing</em> reads by model tier
 * (MD/CT vs household) and meter class ({@link MeterDTO#isMD()}).
 * <p>
 * Shared event log reads ({@code event_log}, same OBIS for MD and household) do not use these filters.
 * Only household recharge/management token events use a separate tier and dedicated tables.
 */
public final class ProfileMeterEligibility {

    private ProfileMeterEligibility() {
    }

    public static boolean isHouseholdModel(MeterDTO dto, Set<String> householdMeterModels) {
        return dto != null
                && dto.getMeterModel() != null
                && householdMeterModels != null
                && householdMeterModels.contains(dto.getMeterModel());
    }

    /**
     * MD/CT profile jobs (channel 1/2, daily/monthly billing): skip household meter models entirely.
     */
    public static Set<String> householdModelsToExclude(Set<String> householdMeterModels) {
        if (householdMeterModels == null || householdMeterModels.isEmpty()) {
            return null;
        }
        return householdMeterModels;
    }

    /**
     * Household-tier jobs (-hh profiles, household billing, household token events): skip MD-class meters.
     */
    public static Predicate<MeterDTO> excludeMdClassMeters() {
        return dto -> dto != null && !dto.isMD();
    }
}
