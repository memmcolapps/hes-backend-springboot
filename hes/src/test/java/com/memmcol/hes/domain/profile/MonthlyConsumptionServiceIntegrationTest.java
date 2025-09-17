package com.memmcol.hes.domain.profile;

import com.memmcol.hes.entities.MonthlyBillingEntity;
import com.memmcol.hes.entities.MonthlyConsumptionEntity;
import com.memmcol.hes.repository.MonthlyBillingRepository;
import com.memmcol.hes.repository.MonthlyConsumptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
@Transactional
@Rollback // rollback after test to keep DB clean
class MonthlyConsumptionServiceIntegrationTest {

    @Autowired
    private MonthlyConsumptionService service;

    @Autowired
    private MonthlyBillingRepository billingRepo;

    @Autowired
    private MonthlyConsumptionRepository consumptionRepo;

    private final String meterSerial = "202006001314";

    @BeforeEach
    void setup() {
        // Insert previous month billing record
        billingRepo.save(MonthlyBillingEntity.builder()
                .meterSerial(meterSerial)
                .entryTimestamp(LocalDateTime.of(2025, 3, 1, 0, 0))
                .totalAbsoluteActiveEnergy(576350.48)
                .meterModel("MMX-313-CT")
                .build());

        // Insert current month billing record
        billingRepo.save(MonthlyBillingEntity.builder()
                .meterSerial(meterSerial)
                .entryTimestamp(LocalDateTime.of(2025, 4, 1, 0, 0))
                .totalAbsoluteActiveEnergy(672744.24)
                .meterModel("MMX-313-CT")
                .build());
    }

    @Test
    void testCalculateMonthlyConsumption() {
        YearMonth targetMonth = YearMonth.of(2025, 4);

        var dto = service.calculateMonthlyConsumption(meterSerial, targetMonth);

        assertThat(dto).isNotNull();
        assertThat(dto.getMeterSerial()).isEqualTo(meterSerial);
        assertThat(dto.getPrevValueKwh()).isEqualTo(576350.48);
        assertThat(dto.getCurrValueKwh()).isEqualTo(672744.24);
        assertThat(dto.getConsumptionKwh()).isEqualTo(96393.76);

        // Verify DB persistence
        MonthlyConsumptionEntity saved =
                consumptionRepo.findByMeterSerialAndMonthStart(meterSerial, targetMonth.atDay(1)).orElseThrow();
        assertThat(saved.getConsumptionKwh()).isEqualTo(96393.76);
    }
}