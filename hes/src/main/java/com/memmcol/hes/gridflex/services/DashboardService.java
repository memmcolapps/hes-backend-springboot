package com.memmcol.hes.gridflex.services;

import com.memmcol.hes.gridflex.records.DashboardSummaryResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DashboardService {

    public DashboardSummaryResponse getDashboardSummary() {
        // 🧮 1. Simulated summary counts (replace with DB queries or repository calls)
        DashboardSummaryResponse.MeterSummary meterSummary =
                new DashboardSummaryResponse.MeterSummary(4200, 1200, 400, 20);

        // 📈 2. Communication log graph points (last 24 hours)
        List<DashboardSummaryResponse.CommunicationLogPoint> communicationLogs = List.of(
                new DashboardSummaryResponse.CommunicationLogPoint("4 hrs", 60),
                new DashboardSummaryResponse.CommunicationLogPoint("8 hrs", 40),
                new DashboardSummaryResponse.CommunicationLogPoint("12 hrs", 45),
                new DashboardSummaryResponse.CommunicationLogPoint("16 hrs", 35),
                new DashboardSummaryResponse.CommunicationLogPoint("20 hrs", 50),
                new DashboardSummaryResponse.CommunicationLogPoint("24 hrs", 80)
        );

        // 🔁 3. Data collection scheduler rates
        DashboardSummaryResponse.DataSchedulerRate schedulerRate =
                new DashboardSummaryResponse.DataSchedulerRate(92.5, 7.5);

        // 📋 4. Communication report table (sample data)
        List<DashboardSummaryResponse.CommunicationReportRow> communicationReport = List.of(
                new DashboardSummaryResponse.CommunicationReportRow("01", "6212465987", "MMX 310-NG", "Offline", "1 min ago", "No Tamper", "2 hours ago", "Disconnected", "2 hours ago"),
                new DashboardSummaryResponse.CommunicationReportRow("02", "6212465987", "MMX 110-NG", "Online", "2 hours ago", "Tamper Detected", "3 hours ago", "Disconnected", "3 hours ago"),
                new DashboardSummaryResponse.CommunicationReportRow("03", "6212465987", "MMX 110-NG", "Offline", "2 hours ago", "No Tamper", "3 hours ago", "Disconnected", "3 hours ago")
        );

        // 🧩 5. Combine all sections
        return new DashboardSummaryResponse(
                meterSummary,
                communicationLogs,
                schedulerRate,
                communicationReport
        );
    }

    public DashboardSummaryResponse getDashboardOverview(String band, String meterType, Integer year) {
        return null;
    }
}
