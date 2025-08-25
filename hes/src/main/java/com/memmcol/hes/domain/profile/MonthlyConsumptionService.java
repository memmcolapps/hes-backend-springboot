package com.memmcol.hes.domain.profile;

import com.memmcol.hes.dto.MonthlyConsumptionDTO;
import com.memmcol.hes.entities.MonthlyBillingEntity;
import com.memmcol.hes.entities.MonthlyConsumptionEntity;
import com.memmcol.hes.repository.MonthlyBillingRepository;
import com.memmcol.hes.repository.MonthlyConsumptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
@Slf4j
public class MonthlyConsumptionService {

    private final MonthlyBillingRepository billingRepo;
    private final MonthlyConsumptionRepository consumptionRepo;

    /**
     * Calculate consumption for a meter between previous month and current month.
     */
    public MonthlyConsumptionDTO calculateMonthlyConsumption(String meterSerial, YearMonth month) {
        LocalDate monthStart = month.atDay(1);
        LocalDate prevMonthStart = month.minusMonths(1).atDay(1);

        // Fetch billing records (previous and current)
        MonthlyBillingEntity prev = billingRepo
                .findByMeterSerialAndEntryTimestamp(meterSerial, prevMonthStart.atStartOfDay())
                .orElseThrow(() -> new IllegalStateException("No billing record for " + prevMonthStart));

        MonthlyBillingEntity curr = billingRepo
                .findByMeterSerialAndEntryTimestamp(meterSerial, monthStart.atStartOfDay())
                .orElseThrow(() -> new IllegalStateException("No billing record for " + monthStart));

        //Calculate consumption
        Double prevVal = prev.getTotalAbsoluteActiveEnergy();
        Double currVal = curr.getTotalAbsoluteActiveEnergy();
        Double consumption = (currVal != null && prevVal != null) ? (currVal - prevVal) : null;

        // Persist to monthly_consumption
        MonthlyConsumptionEntity entity = MonthlyConsumptionEntity.builder()
                .meterSerial(meterSerial)
                .monthStart(monthStart)
                .meterModel(curr.getMeterModel())
                .prevValueKwh(prevVal)
                .currValueKwh(currVal)
                .consumptionKwh(consumption)
                .build();

        consumptionRepo.save(entity);

        return MonthlyConsumptionDTO.builder()
                .meterSerial(meterSerial)
                .monthStart(monthStart)
                .prevValueKwh(prevVal)
                .currValueKwh(currVal)
                .consumptionKwh(consumption)
                .build();
    }
}
