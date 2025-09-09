package com.memmcol.hes.controller;

import com.memmcol.hes.domain.profile.MonthlyConsumptionService;
import com.memmcol.hes.dto.MonthlyConsumptionDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/consumption")
@RequiredArgsConstructor
@Tag(name = "Consumption Report", description = "Endpoints for calculating and retrieving consumptions")
public class ConsumptionController {
    private final MonthlyConsumptionService monthlyConsumptionService;

    @Operation(
            summary = "Calculate monthly consumption",
            description = "Calculates monthly kWh consumption for the given meter and month, then stores it in the DB"
    )
    @PostMapping("/{meterSerial}/calculate")
    public ResponseEntity<MonthlyConsumptionDTO> calculateConsumption(
            @PathVariable String meterSerial,
            @Parameter(description = "Month to calculate (format: yyyy-MM)")
            @RequestParam("month")
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {

        MonthlyConsumptionDTO result = monthlyConsumptionService.calculateMonthlyConsumption(meterSerial, month);
        if (result == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(result);
    }
}
