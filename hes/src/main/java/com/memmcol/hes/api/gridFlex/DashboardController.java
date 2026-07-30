package com.memmcol.hes.api.gridFlex;

import com.memmcol.hes.gridflex.records.DashboardSummaryResponse;
import com.memmcol.hes.gridflex.services.DashboardService;
import com.memmcol.hes.model.MetersConnectionEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Tag(name = "Dashboard", description = "API for Gridflex dashboard overview")
public class DashboardController {
    private final DashboardService dashboardService;
    private final CacheManager cacheManager;

    @GetMapping("/dashboard/summary")
    @Operation(summary = "Get HES dashboard overview on load")
    public ResponseEntity<DashboardSummaryResponse> getDashboardSummary() {
        DashboardSummaryResponse response = dashboardService.getDashboardSummary();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/meter-connection")
    @Operation(summary = "Get meter connection event")
    public ResponseEntity<?> getMeterConnection(@RequestParam String serial) {
        MetersConnectionEvent response = dashboardService.getMeterConnection(serial);
        if (response == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Meter connection not found");
        }
        return ResponseEntity.ok(response);
    }

//    @GetMapping("/overview")
//    @Operation(summary = "Get HES dashboard overview on demand")
//    public ResponseEntity<DashboardSummaryResponse> getDashboard(
//            @RequestParam(required = false) String band,
//            @RequestParam(required = false) String meterType,
//            @RequestParam(required = false) Integer year) {
//
//        DashboardSummaryResponse response = dashboardService.getDashboardOverview(band, meterType, year);
//        return ResponseEntity.ok(response);
//    }

    @GetMapping("/cache/clear")
    @Operation(summary = "Clear or Inspect Cache")
    public String clearDashboardCache() {
        Objects.requireNonNull(cacheManager.getCache("dashboardMeterSummary")).clear();
        Objects.requireNonNull(cacheManager.getCache("dashboardCommunicationLogs")).clear();
        Objects.requireNonNull(cacheManager.getCache("dashboardSchedulerRate")).clear();
        Objects.requireNonNull(cacheManager.getCache("dashboardCommunicationReport")).clear();
        return "Dashboard caches cleared.";
    }
}
