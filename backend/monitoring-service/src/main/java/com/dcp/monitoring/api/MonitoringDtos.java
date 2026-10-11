package com.dcp.monitoring.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MonitoringDtos {

    private MonitoringDtos() {
    }

    public record UpsertTargetRequest(
            @NotNull UUID applicationId,
            @NotNull UUID ownerId,
            @NotBlank String deploymentName,
            @NotBlank String namespace) {
    }

    public record MonitorTargetView(
            UUID id,
            UUID applicationId,
            UUID ownerId,
            String deploymentName,
            String namespace,
            Instant createdAt) {
    }

    public record TargetAuditEvent(
            UUID id,
            String action,
            UUID applicationId,
            UUID ownerId,
            String deploymentName,
            String namespace,
            Instant createdAt) {
    }

    public record OverviewView(
            boolean prometheusUp,
            String message,
            String namespace,
            Double cpuCores,
            Double memoryBytes,
            Double podCount,
            Double restartCount,
            Double controlPlaneHeapBytes,
            Instant scrapedAt) {
    }

    public record SeriesView(
            String metric,
            String range,
            List<Long> timestamps,
            List<Double> values,
            String message) {
    }

    public record GrafanaView(String url, String dashboardPath) {
    }
}
