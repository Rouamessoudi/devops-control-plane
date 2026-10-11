package com.dcp.monitoring.service;

import com.dcp.common.error.ApiException;
import com.dcp.common.policy.ResourcePolicies;
import com.dcp.common.security.JwtPrincipal;
import com.dcp.common.web.CurrentUser;
import com.dcp.common.web.InternalRestClient;
import com.dcp.monitoring.api.MonitoringDtos.GrafanaView;
import com.dcp.monitoring.api.MonitoringDtos.MonitorTargetView;
import com.dcp.monitoring.api.MonitoringDtos.OverviewView;
import com.dcp.monitoring.api.MonitoringDtos.SeriesView;
import com.dcp.monitoring.api.MonitoringDtos.TargetAuditEvent;
import com.dcp.monitoring.api.MonitoringDtos.UpsertTargetRequest;
import com.dcp.monitoring.config.MonitoringProperties.GrafanaProperties;
import com.dcp.monitoring.domain.MonitorTargetEntity;
import com.dcp.monitoring.domain.MonitorTargetRepository;
import com.dcp.monitoring.prometheus.PromqlCatalog;
import com.dcp.monitoring.prometheus.PrometheusClient;
import com.dcp.monitoring.prometheus.PrometheusVectors.SeriesPoints;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class MonitoringService {

    private final MonitorTargetRepository targets;
    private final PromqlCatalog catalog;
    private final PrometheusClient prometheus;
    private final GrafanaProperties grafana;
    private final InternalRestClient kubernetesClient;

    public MonitoringService(MonitorTargetRepository targets,
                             PromqlCatalog catalog,
                             PrometheusClient prometheus,
                             GrafanaProperties grafana,
                             InternalRestClient kubernetesClient) {
        this.targets = targets;
        this.catalog = catalog;
        this.prometheus = prometheus;
        this.grafana = grafana;
        this.kubernetesClient = kubernetesClient;
    }

    public OverviewView overview() {
        Double cpu = prometheus.instant(catalog.cpu());
        Double memory = prometheus.instant(catalog.memory());
        Double pods = prometheus.instant(catalog.pods());
        if (pods == null) {
            pods = prometheus.instant(catalog.podsByPhase());
        }
        Double restarts = prometheus.instant(catalog.restarts());
        Double heap = prometheus.instant(catalog.controlPlaneHeap());
        boolean platformMissing = cpu == null && memory == null && heap == null;
        String message = platformMissing ? PromqlCatalog.NO_SAMPLES : null;
        if (!platformMissing && restarts == null) {
            message = "Kubernetes pod metrics are absent. CPU and memory come from Prometheus actuator series.";
        }
        return new OverviewView(
                true,
                message,
                catalog.namespace(),
                cpu,
                memory,
                pods,
                restarts,
                heap,
                Instant.now());
    }

    public SeriesView series(String metric, String range) {
        String promql = catalog.series(metric);
        SeriesPoints points = prometheus.range(promql, PromqlCatalog.range(range), PromqlCatalog.stepSeconds(range));
        String message = points.timestamps().isEmpty() ? PromqlCatalog.NO_SAMPLES : null;
        return new SeriesView(metric, range, points.timestamps(), points.values(), message);
    }

    public GrafanaView grafana() {
        return new GrafanaView(grafana.getUrl(), grafana.getDashboardPath());
    }

    @Transactional(readOnly = true)
    public List<MonitorTargetView> targets() {
        List<MonitorTargetView> views = new ArrayList<>();
        for (MonitorTargetEntity row : visible()) {
            views.add(view(row));
        }
        return views;
    }

    @Transactional(readOnly = true)
    public List<TargetAuditEvent> audit() {
        List<TargetAuditEvent> events = new ArrayList<>();
        for (MonitorTargetEntity row : visible()) {
            events.add(new TargetAuditEvent(
                    row.getId(),
                    "UPSERT",
                    row.getApplicationId(),
                    row.getOwnerId(),
                    row.getDeploymentName(),
                    row.getNamespaceName(),
                    row.getCreatedAt()));
        }
        return events;
    }

    public String logs(String pod, Integer tail) {
        String name = ResourcePolicies.dnsLabel(pod, "pod");
        int lines = tail == null ? 200 : tail;
        if (lines < 1 || lines > 5000) {
            throw ApiException.badRequest("tail must be between 1 and 5000");
        }
        String body = kubernetesClient.get("/internal/kubernetes/pods/" + name + "/logs?tail=" + lines, String.class);
        return body == null ? "" : body;
    }

    public String events() {
        String body = kubernetesClient.get("/internal/kubernetes/events", String.class);
        return body == null ? "" : body;
    }

    @Transactional
    public MonitorTargetView upsert(UpsertTargetRequest request) {
        String deploymentName = ResourcePolicies.dnsLabel(request.deploymentName(), "deploymentName");
        String namespace = ResourcePolicies.dnsLabel(request.namespace(), "namespace");
        MonitorTargetEntity row = targets.findByApplicationId(request.applicationId()).orElse(null);
        if (row == null) {
            row = new MonitorTargetEntity();
            row.setId(UUID.randomUUID());
            row.setApplicationId(request.applicationId());
            row.setCreatedAt(Instant.now());
        }
        row.setOwnerId(request.ownerId());
        row.setDeploymentName(deploymentName);
        row.setNamespaceName(namespace);
        return view(targets.save(row));
    }

    private List<MonitorTargetEntity> visible() {
        JwtPrincipal user = CurrentUser.require();
        if (user.hasRole("ADMIN")) {
            return targets.findAllByOrderByCreatedAtDesc();
        }
        return targets.findByOwnerIdOrderByCreatedAtDesc(user.userId());
    }

    private static MonitorTargetView view(MonitorTargetEntity row) {
        return new MonitorTargetView(
                row.getId(),
                row.getApplicationId(),
                row.getOwnerId(),
                row.getDeploymentName(),
                row.getNamespaceName(),
                row.getCreatedAt());
    }
}
