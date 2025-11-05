package com.memmcol.hes.gridflex.services;

import com.memmcol.hes.gridflex.records.DashboardSummaryResponse;
import com.memmcol.hes.model.MetersConnectionEvent;
import com.memmcol.hes.netty.NettyServerHolder;
import com.memmcol.hes.repository.*;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


@AllArgsConstructor
@Deprecated
public class DashboardServiceV1 {
    private final SmartMeterRepository smartMeterRepository;
    private final NettyServerHolder holder;
    private final MetersConnectionEventRepository connectionEventRepository;
    private final SchedulerRepository schedulerRepository;
    private final MeterRepository meterRepository;
    private final MetersConnectionEventRepository metersConnectionEventRepository;
    private final EventLogRepository eventLogRepository;

    public DashboardSummaryResponse getDashboardSummary() {
        // 🧮 1️⃣ Meter summary
        DashboardSummaryResponse.MeterSummary meterSummary = getMeterSummary();

        // 📈 2. Communication log graph points (last 24 hours)
        List<DashboardSummaryResponse.CommunicationLogPoint> communicationLogs = getCommunicationLog();

        // 🔁 3️⃣ Scheduler rate
        DashboardSummaryResponse.DataSchedulerRate schedulerRate = getSchedulerRate();

        // 📋 4. Communication report table (sample data)
        List<DashboardSummaryResponse.CommunicationReportRow> communicationReport = getCommunicationReport();

        // 🧩 5. Combine all sections
        return new DashboardSummaryResponse(
                meterSummary,
                communicationLogs,
                schedulerRate,
                communicationReport
        );
    }

    public DashboardSummaryResponse.MeterSummary getMeterSummary() {
        // 🧮 1️⃣ Meter summary
        int total = Optional.of(smartMeterRepository.countAll()).orElse(0);
        int online = Optional.of(holder.getActiveMeterCount()).orElse(0);

        // Ensure online doesn’t exceed total
        int offline = Math.max(total - online, 0);
        int failedCommands = 0; // Placeholder for now
        return new DashboardSummaryResponse.MeterSummary(total, online, offline, failedCommands);
    }

    // 📈 2️⃣ Communication Log Graph
    public List<DashboardSummaryResponse.CommunicationLogPoint> getCommunicationLog() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime fromTime = now.minusHours(24);

        List<MetersConnectionEvent> recentEvents = connectionEventRepository.findRecentEvents(fromTime);

        // Define 6 intervals (4 hours each)
        List<DashboardSummaryResponse.CommunicationLogPoint> points = new ArrayList<>();
        for (int i = 4; i <= 24; i += 4) {
            LocalDateTime start = now.minusHours(i);
            LocalDateTime end = now.minusHours(i - 4);

            long count = recentEvents.stream()
                    .filter(e -> e.getUpdatedAt().isAfter(start) && e.getUpdatedAt().isBefore(end))
                    .count();

            points.add(new DashboardSummaryResponse.CommunicationLogPoint(i + " hrs", (int) count));
        }

        return points;
    }

    public DashboardSummaryResponse.DataSchedulerRate getSchedulerRate() {

        long active = schedulerRepository.countByJobStatusIgnoreCase("ACTIVE");
        long paused = schedulerRepository.countByJobStatusIgnoreCase("PAUSED");

        double total = active + paused;
        double activePercent = 0.0;
        double pausedPercent = 0.0;

        if (total > 0) {
            activePercent = (active / total) * 100.0;
            pausedPercent = (paused / total) * 100.0;
        }

        return new DashboardSummaryResponse.DataSchedulerRate(activePercent, pausedPercent);
    }

    private List<DashboardSummaryResponse.CommunicationReportRow> getCommunicationReport(){
        // 📋 4️⃣ Communication report table
        List<DashboardSummaryResponse.CommunicationReportRow> communicationReport = new ArrayList<>();

        List<Object[]> meterModels = meterRepository.findAllMeterModels();
        Map<String, String> meterModelMap = meterModels.stream()
                .collect(Collectors.toMap(r -> (String) r[0], r -> (String) r[1]));

        List<Object[]> connectionEvents = metersConnectionEventRepository.findLatestConnectionEvents();
        Map<String, Object[]> connectionMap = connectionEvents.stream()
                .collect(Collectors.toMap(r -> (String) r[0], r -> r));

        List<Object[]> tamperEvents = eventLogRepository.findLatestEventLogsByType(3); // Tamper
        Map<String, Object[]> tamperMap = tamperEvents.stream()
                .collect(Collectors.toMap(r -> (String) r[0], r -> r));

        List<Object[]> relayEvents = eventLogRepository.findLatestEventLogsByType(4); // Relay
        Map<String, Object[]> relayMap = relayEvents.stream()
                .collect(Collectors.toMap(r -> (String) r[0], r -> r));

        int sn = 1;
        for (String meterNo : meterModelMap.keySet()) {
            Object[] conn = connectionMap.get(meterNo);
            Object[] tamp = tamperMap.get(meterNo);
            Object[] relay = relayMap.get(meterNo);

            communicationReport.add(
                    new DashboardSummaryResponse.CommunicationReportRow(
                            String.format("%02d", sn++),
                            meterNo,
                            meterModelMap.get(meterNo),
                            conn != null ? (String) conn[1] : "Unknown",
                            conn != null ? conn[2].toString() : "N/A",
                            tamp != null ? (String) tamp[1] : "No Tamper",
                            tamp != null ? tamp[2].toString() : "N/A",
                            relay != null ? (String) relay[1] : "Disconnected",
                            relay != null ? relay[2].toString() : "N/A"
                    )
            );
        }
        return communicationReport;
    }


    public DashboardSummaryResponse getDashboardOverview(String band, String meterType, Integer year) {
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
}
