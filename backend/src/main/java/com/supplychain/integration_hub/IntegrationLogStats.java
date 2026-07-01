package com.supplychain.integration_hub;

public record IntegrationLogStats(
        long totalLogs,
        long successLogs,
        long failedLogs,
        long pendingLogs,
        double successRate) {
}
