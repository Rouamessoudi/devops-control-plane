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
        return "sum(process_cpu_usage{job=\"devops-control-plane\"}) or sum(rate(container_cpu_usage_seconds_total{namespace=\""
                + namespace + "\",container!=\"\"}[5m]))";
    }

    public String memory() {
        return "sum(jvm_memory_used_bytes{job=\"devops-control-plane\"}) or sum(container_memory_working_set_bytes{namespace=\""
                + namespace + "\",container!=\"\"})";
    }

    public String pods() {
        return "count(kube_pod_info{namespace=\"" + namespace + "\"}) or count(up{job=\"devops-control-plane\"} == 1)";
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

    public String series(String metric) {
        if ("cpu".equals(metric)) {
            return cpu();
        }
        if ("memory".equals(metric)) {
            return memory();
        }
        throw ApiException.badRequest("metric must be cpu or memory");
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
