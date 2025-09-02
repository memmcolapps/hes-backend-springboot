package com.memmcol.hes.domain.profile;

import com.memmcol.hes.dto.MonthlyConsumptionDTO;
import com.memmcol.hes.entities.MonthlyBillingEntity;
import com.memmcol.hes.entities.MonthlyConsumptionEntity;
import com.memmcol.hes.infrastructure.persistence.PartitionService;
import com.memmcol.hes.repository.MonthlyBillingRepository;
import com.memmcol.hes.repository.MonthlyConsumptionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
@Slf4j
public class MonthlyConsumptionService {

    private final MonthlyBillingRepository billingRepo;
    private final MonthlyConsumptionRepository consumptionRepo;
    private final PartitionService partitionService;
    @PersistenceContext
    private EntityManager em;

    /**
     * Calculate consumption for a meter between previous month and current month.
     */
    public MonthlyConsumptionDTO calculateMonthlyConsumption(String meterSerial, YearMonth month) {
        LocalDate monthStart = month.atDay(1);
        LocalDate prevMonthStart = month.minusMonths(1).atDay(1);

        // 1. Ensure partition exists before save
        // 1. Ensure partition exists (transactional via PartitionService)
        partitionService.ensureMonthlyPartition(month);

        // 2. Fetch billing records (previous and current)
        MonthlyBillingEntity prev = billingRepo
                .findByMeterSerialAndEntryTimestamp(meterSerial, prevMonthStart.atStartOfDay())
                .orElseThrow(() -> new IllegalStateException("No billing record for " + prevMonthStart));

        MonthlyBillingEntity curr = billingRepo
                .findByMeterSerialAndEntryTimestamp(meterSerial, monthStart.atStartOfDay())
                .orElseThrow(() -> new IllegalStateException("No billing record for " + monthStart));

        // 3. Calculate consumption
        Double prevVal = prev.getTotalAbsoluteActiveEnergy();
        Double currVal = curr.getTotalAbsoluteActiveEnergy();

        // Assuming prevVal and currVal are Double (nullable)
        BigDecimal consumptionBd = null;
        if (currVal != null && prevVal != null) {
            consumptionBd = BigDecimal.valueOf(currVal)
                    .subtract(BigDecimal.valueOf(prevVal))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // If you must keep Double:
        Double consumption = (consumptionBd == null) ? null : consumptionBd.doubleValue();

        // 4. Persist to monthly_consumption
        MonthlyConsumptionEntity entity = MonthlyConsumptionEntity.builder()
                .meterSerial(meterSerial)
                .monthStart(monthStart)
                .meterModel(curr.getMeterModel())
                .prevValueKwh(prevVal)
                .currValueKwh(currVal)
                .consumptionKwh(consumption)
                .build();

        consumptionRepo.save(entity);

        MonthlyConsumptionDTO consumptionDTO = MonthlyConsumptionDTO.builder()
                .meterSerial(meterSerial)
                .monthStart(monthStart)
                .prevValueKwh(prevVal)
                .currValueKwh(currVal)
                .consumptionKwh(consumption)
                .build();

        log.info("MonthlyConsumption calculated: {}", consumptionDTO.toString());
        return consumptionDTO;
    }

}
