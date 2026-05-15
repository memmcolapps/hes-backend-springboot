package com.memmcol.hes.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Declarative OBIS tiers per {@link com.memmcol.hes.domain.events.EventScheduleProfile#configKey()}.
 * Scheduler {@code obisCodes} remains the default MD/CT OBIS unless {@link ProfileTierObis#mdCt} is set here.
 */
@ConfigurationProperties(prefix = "hes.events")
public class HesEventObisProperties {

    /**
     * Map key = profile id, e.g. {@code recharge-token}, {@code management-token}.
     */
    private Map<String, ProfileTierObis> profiles = new LinkedHashMap<>();

    public Map<String, ProfileTierObis> getProfiles() {
        return profiles;
    }

    public void setProfiles(Map<String, ProfileTierObis> profiles) {
        this.profiles = profiles != null ? profiles : new LinkedHashMap<>();
    }

    public static class ProfileTierObis {
        /**
         * Optional fallback when the Quartz job does not supply {@code obisCodes}.
         */
        private String mdCt = "";
        /**
         * Household-only OBIS for this profile (required for household tier to run).
         */
        private String household = "";

        public String getMdCt() {
            return mdCt;
        }

        public void setMdCt(String mdCt) {
            this.mdCt = mdCt;
        }

        public String getHousehold() {
            return household;
        }

        public void setHousehold(String household) {
            this.household = household;
        }
    }
}
