package com.memmcol.hes.domain.profile;

import com.memmcol.hes.dto.MeterDTO;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileMeterEligibilityTest {

    @Test
    void excludeMdClassMeters_skipsMdMeterClass() {
        MeterDTO md = MeterDTO.builder().meterModel("HH-1P").meterClass("MD").build();
        md.determineMD();
        MeterDTO ct = MeterDTO.builder().meterModel("HH-1P").meterClass("CT").build();
        ct.determineMD();

        var filter = ProfileMeterEligibility.excludeMdClassMeters();
        assertFalse(filter.test(md));
        assertTrue(filter.test(ct));
    }

    @Test
    void householdModelsToExclude_returnsSetWhenConfigured() {
        Set<String> hh = Set.of("MODEL_A");
        assertTrue(ProfileMeterEligibility.isHouseholdModel(
                MeterDTO.builder().meterModel("MODEL_A").build(), hh));
        assertFalse(ProfileMeterEligibility.isHouseholdModel(
                MeterDTO.builder().meterModel("MODEL_B").build(), hh));
    }
}
