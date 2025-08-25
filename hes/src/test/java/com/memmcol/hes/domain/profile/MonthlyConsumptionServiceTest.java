package com.memmcol.hes.domain.profile;

import com.memmcol.hes.dto.MonthlyConsumptionDTO;
import com.memmcol.hes.entities.MonthlyBillingEntity;
import com.memmcol.hes.entities.MonthlyConsumptionEntity;
import com.memmcol.hes.repository.MonthlyBillingRepository;
import com.memmcol.hes.repository.MonthlyConsumptionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.YearMonth;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class MonthlyConsumptionServiceTest {

    @Mock
    private MonthlyBillingRepository billingRepo;

    @Mock
    private MonthlyConsumptionRepository consumptionRepo;

    @InjectMocks
    private MonthlyConsumptionService service;

    public MonthlyConsumptionServiceTest() {
        MockitoAnnotations.openMocks(this);
    }


    @Test
    void testCalculateMonthlyConsumption() {
        String meterSerial = "202006001314";
        YearMonth targetMonth = YearMonth.of(2025, 8);
        YearMonth prevMonth = targetMonth.minusMonths(1);

        // Fake billing records
        MonthlyBillingEntity prev = MonthlyBillingEntity.builder()
                .meterSerial(meterSerial)
                .entryTimestamp(prevMonth.atDay(1).atStartOfDay())
                .totalAbsoluteActiveEnergy(1000.0)
                .build();

        MonthlyBillingEntity curr = MonthlyBillingEntity.builder()
                .meterSerial(meterSerial)
                .entryTimestamp(targetMonth.atDay(1).atStartOfDay())
                .totalAbsoluteActiveEnergy(1200.0)
                .build();

        // Mock repo calls
        when(billingRepo.findByMeterSerialAndEntryTimestamp(meterSerial, prev.getEntryTimestamp()))
                .thenReturn(Optional.of(prev));
        when(billingRepo.findByMeterSerialAndEntryTimestamp(meterSerial, curr.getEntryTimestamp()))
                .thenReturn(Optional.of(curr));

        // Expectation: consumption = 200
        MonthlyConsumptionEntity expected = MonthlyConsumptionEntity.builder()
                .meterSerial(meterSerial)
                .monthStart(targetMonth.atDay(1))
                .prevValueKwh(1000.0)
                .currValueKwh(1200.0)
                .consumptionKwh(200.0)
                .build();

        when(consumptionRepo.save(any(MonthlyConsumptionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0)); // return same entity

        // Act
        MonthlyConsumptionDTO result = service.calculateMonthlyConsumption(meterSerial, targetMonth);

        // Assert
        assertNotNull(result);
        assertEquals(200.0, result.getConsumptionKwh(), 0.001);
        assertEquals(targetMonth.atDay(1), result.getMonthStart());
        assertEquals(1000.0, result.getPrevValueKwh());
        assertEquals(1200.0, result.getCurrValueKwh());

        // Verify interactions
        verify(billingRepo, times(1))
                .findByMeterSerialAndEntryTimestamp(eq(meterSerial), eq(prev.getEntryTimestamp()));
        verify(billingRepo, times(1))
                .findByMeterSerialAndEntryTimestamp(eq(meterSerial), eq(curr.getEntryTimestamp()));
        verify(consumptionRepo, times(1)).save(any(MonthlyConsumptionEntity.class));
    }
}
