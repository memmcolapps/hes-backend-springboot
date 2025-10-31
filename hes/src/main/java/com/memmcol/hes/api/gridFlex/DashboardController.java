package com.memmcol.hes.api.gridFlex;

import com.memmcol.hes.gridflex.records.DashboardSummaryResponse;
import com.memmcol.hes.gridflex.services.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "API for Gridflex dashboard overview")
public class DashboardController {
    private final DashboardService dashboardService;

    @GetMapping
    @Operation(summary = "Get HES dashboard overview on load")
    public ResponseEntity<DashboardSummaryResponse> getDashboardSummary() {
        DashboardSummaryResponse response = dashboardService.getDashboardSummary();
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Get HES dashboard overview on demand")
    public ResponseEntity<DashboardSummaryResponse> getDashboard(
            @RequestParam(required = false) String band,
            @RequestParam(required = false) String meterType,
            @RequestParam(required = false) Integer year) {

        DashboardSummaryResponse response = dashboardService.getDashboardOverview(band, meterType, year);
        return ResponseEntity.ok(response);
    }
}
