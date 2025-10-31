package com.memmcol.hes.gridflex.records;

import java.util.List;

public record DashboardSummaryResponse(
        MeterSummary meterSummary,
        List<CommunicationLogPoint> communicationLogs,
        DataSchedulerRate schedulerRate,
        List<CommunicationReportRow> communicationReport
) {
    public record MeterSummary(
            int totalSmartMeters,
            int online,
            int offline,
            int failedCommands
    ) {}

    public record CommunicationLogPoint(
            String timeLabel,   // e.g. "4 hrs", "8 hrs"
            int value           // e.g. communication count
    ) {}

    public record DataSchedulerRate(
            double activeRate,  // percentage active
            double pausedRate   // percentage paused
    ) {}

    public record CommunicationReportRow(
            String serialNumber,
            String meterNo,
            String meterModel,
            String status,        // Online / Offline
            String lastSync,      // "1 min ago", "2 hours ago"
            String tamperState,   // "No Tamper", "Tamper Detected"
            String tamperSync,    // e.g. "2 hours ago"
            String relayControl,  // Connected / Disconnected
            String relaySync      // e.g. "2 hours ago"
    ) {}
}
