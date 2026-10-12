package com.dcp.monitoring.prometheus;

import com.dcp.common.error.ApiException;
import com.dcp.common.policy.ResourcePolicies;

import java.time.Duration;

public final class PromqlCatalog {

    public static final String NO_SAMPLES = "No samples in Prometheus for this query";

    private final String namespace;

    public PromqlCatalog(String namespace) {
        this.namespace = ResourcePolicies.dnsLabel(namespace, "namespace");
    }

    public String namespace() {
        return namespace;
    }

    public String cpu() {
        return "sum(process_cpu_usage{job=\"devops-control-plane\"})";
    }

    public String memory() {
        return "sum(jvm_memory_used_bytes{job=\"devops-control-plane\"})";
    }

    public String pods() {
        return "count(kube_pod_info{namespace=\"" + namespace + "\"})";
    }

    public String podsByPhase() {
        return "count(kube_pod_status_phase{namespace=\"" + namespace + "\"})";
    }

    public String restarts() {
        return "sum(kube_pod_container_status_restarts_total{namespace=\"" + namespace + "\"})";
    }

    public String controlPlaneHeap() {
        return "sum(jvm_memory_used_bytes{area=\"heap\"})";
    }

    public String httpRate() {
        return "sum(rate(http_server_requests_seconds_count{job=\"devops-control-plane\"}[5m]))";
    }

    public String httpErrors() {
        return "sum(rate(http_server_requests_seconds_count{job=\"devops-control-plane\",status=~\"5..\"}[5m]))";
    }

    public String httpLatency() {
        return "sum(rate(http_server_requests_seconds_sum{job=\"devops-control-plane\"}[5m])) / sum(rate(http_server_requests_seconds_count{job=\"devops-control-plane\"}[5m]))";
    }

    public String httpClientErrors() {
        return "sum(rate(http_server_requests_seconds_count{job=\"devops-control-plane\",status=~\"4..\"}[5m]))";
    }

    public String availability() {
        return "count(up{job=\"devops-control-plane\"} == 1)";
    }

    public String podCpu() {
        return "sum(rate(container_cpu_usage_seconds_total{namespace=\"" + namespace + "\",container!=\"\",container!=\"POD\"}[5m]))";
    }

    public String podMemory() {
        return "sum(container_memory_working_set_bytes{namespace=\"" + namespace + "\",container!=\"\",container!=\"POD\"})";
    }

    public String availableDeployments() {
        return "sum(kube_deployment_status_replicas_available{namespace=\"" + namespace + "\"})";
    }

    public String series(String metric) {
        if ("cpu".equals(metric)) {
            return cpu();
        }
        if ("memory".equals(metric)) {
            return memory();
        }
        if ("http".equals(metric)) {
            return httpRate();
        }
        if ("errors".equals(metric)) {
            return httpErrors();
        }
        if ("latency".equals(metric)) {
            return httpLatency();
        }
        if ("clientErrors".equals(metric)) {
            return httpClientErrors();
        }
        if ("podCpu".equals(metric)) {
            return podCpu();
        }
        if ("podMemory".equals(metric)) {
            return podMemory();
        }
        if ("restarts".equals(metric)) {
            return restarts();
        }
        if ("deployments".equals(metric)) {
            return availableDeployments();
        }
        throw ApiException.badRequest("metric must be cpu, memory, http, errors, latency, clientErrors, podCpu, podMemory, restarts, or deployments");
    }

    public static Duration range(String value) {
        if ("15m".equals(value)) {
            return Duration.ofMinutes(15);
        }
        if ("1h".equals(value)) {
            return Duration.ofHours(1);
        }
        if ("6h".equals(value)) {
            return Duration.ofHours(6);
        }
        if ("24h".equals(value)) {
            return Duration.ofHours(24);
        }
        throw ApiException.badRequest("range must be 15m, 1h, 6h, or 24h");
    }

    public static long stepSeconds(String value) {
        if ("15m".equals(value) || "1h".equals(value)) {
            return 60;
        }
        if ("6h".equals(value) || "24h".equals(value)) {
            return 300;
        }
        throw ApiException.badRequest("range must be 15m, 1h, 6h, or 24h");
    }
}
