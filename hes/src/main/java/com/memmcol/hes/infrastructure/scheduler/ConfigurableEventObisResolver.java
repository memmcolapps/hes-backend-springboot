package com.memmcol.hes.infrastructure.scheduler;

import com.memmcol.hes.application.port.out.EventObisResolutionPort;
import com.memmcol.hes.config.properties.HesEventObisProperties;
import com.memmcol.hes.domain.events.EventScheduleProfile;
import com.memmcol.hes.domain.events.ResolvedTieredEventObis;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Resolves tiered event OBIS from {@link HesEventObisProperties} with scheduler JobDataMap precedence
 * for MD/CT and optional explicit household override.
 */
@Component
@RequiredArgsConstructor
public class ConfigurableEventObisResolver implements EventObisResolutionPort {

    private final HesEventObisProperties properties;

    @Override
    public ResolvedTieredEventObis resolve(EventScheduleProfile profile,
                                           String schedulerMdCtObis,
                                           String schedulerHouseholdObis) {
        Map<String, HesEventObisProperties.ProfileTierObis> profiles = properties.getProfiles();
        HesEventObisProperties.ProfileTierObis tier =
                profiles != null ? profiles.get(profile.configKey()) : null;

        String fromPropsMdCt = tier != null ? nullToEmpty(tier.getMdCt()) : "";
        String fromPropsHousehold = tier != null ? nullToEmpty(tier.getHousehold()) : "";

        String mdCt = firstNonBlank(schedulerMdCtObis, fromPropsMdCt);
        String household = firstNonBlank(schedulerHouseholdObis, fromPropsHousehold);
        return new ResolvedTieredEventObis(mdCt, household);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }
}
