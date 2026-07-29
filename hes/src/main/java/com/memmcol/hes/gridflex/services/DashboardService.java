package com.memmcol.hes.gridflex.services;

import com.memmcol.hes.gridflex.records.DashboardSummaryResponse;
import com.memmcol.hes.model.MetersConnectionEvent;
import com.memmcol.hes.repository.MeterRepository;
import com.memmcol.hes.repository.MetersConnectionEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {
    private final DashboardAsyncService asyncService;

    private final MetersConnectionEventRepository metersConnectionEventRepository;
    // ======================
    // Main method to call from controller
    // ======================
    public DashboardSummaryResponse getDashboardSummary() {

        // Run all tasks asynchronously in parallel
        CompletableFuture<DashboardSummaryResponse.MeterSummary> meterSummaryFuture = asyncService.getMeterSummaryAsync();
        CompletableFuture<List<DashboardSummaryResponse.CommunicationLogPoint>> communicationLogsFuture = asyncService.getCommunicationLogsAsync();
        CompletableFuture<DashboardSummaryResponse.DataSchedulerRate> schedulerRateFuture = asyncService.getSchedulerRateAsync();
        CompletableFuture<List<DashboardSummaryResponse.CommunicationReportRow>> communicationReportFuture = asyncService.
                getCommunicationReportAsync(0, 5, "lastSync", true);

        // Wait for all to complete
        CompletableFuture.allOf(
                meterSummaryFuture,
                communicationLogsFuture,
                schedulerRateFuture,
                communicationReportFuture
        ).join();

        // Combine results
        return new DashboardSummaryResponse(
                meterSummaryFuture.join(),
                communicationLogsFuture.join(),
                schedulerRateFuture.join(),
                communicationReportFuture.join()
        );
    }


    public MetersConnectionEvent getMeterConnection(String serial) {
        return metersConnectionEventRepository.findByMeterNo(serial)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Meter connection not found"
                ));
    }

}

