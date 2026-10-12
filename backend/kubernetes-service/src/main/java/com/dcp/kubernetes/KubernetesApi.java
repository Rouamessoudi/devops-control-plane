package com.dcp.kubernetes;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class KubernetesApi {

    private KubernetesApi() {
    }

    public record ClusterStatus(boolean reachable, String namespace, String message) {
    }

    public record PodView(
            String name,
            String namespace,
            String phase,
            int restarts,
            String node,
            List<String> images,
            String createdAt,
            String cpu,
            String memory
    ) {
    }

    public record NamespaceView(String name, String status) {
    }

    public record DeploymentView(
            String name,
            String namespace,
            Integer replicas,
            Integer readyReplicas,
            List<String> images,
            String createdAt
    ) {
    }

    public record ServiceView(
            String name,
            String namespace,
            String type,
            String clusterIP,
            List<String> ports,
            String createdAt
    ) {
    }

    public record IngressView(
            String name,
            String namespace,
            List<String> hosts,
            String createdAt
    ) {
    }

    public record EventView(
            String name,
            String namespace,
            String reason,
            String message,
            String type,
            String involvedObject,
            String createdAt
    ) {
    }

    public record EnvBinding(String key, String value, boolean secret) {
    }

    public record DeployRequest(
            @NotNull String name,
            @NotNull String image,
            @NotNull Integer replicas,
            @NotNull Integer containerPort,
            @NotNull UUID actorId,
            @NotNull UUID applicationId,
            List<EnvBinding> env
    ) {
    }

    public record ScaleRequest(@NotNull Integer replicas) {
    }

    public record K8sDeploymentView(
            String name,
            String namespace,
            String image,
            int replicas,
            int readyReplicas,
            String revision,
            String status,
            String message
    ) {
    }

    public record OperationAudit(
            UUID id,
            UUID actorId,
            String operation,
            String resourceName,
            String status,
            String message,
            Instant createdAt
    ) {
    }

    public record BlueGreenRequest(
            @NotNull String name,
            @NotNull String activeImage,
            @NotNull String candidateImage,
            @NotNull Integer replicas,
            @NotNull Integer containerPort,
            @NotNull UUID applicationId,
            @NotNull String candidateTrack
    ) {
    }

    public record CanaryRequest(
            @NotNull String name,
            @NotNull String stableImage,
            @NotNull String canaryImage,
            @NotNull Integer replicas,
            @NotNull Integer containerPort,
            @NotNull UUID applicationId,
            @NotNull Integer canaryWeight
    ) {
    }

    public record HpaRequest(
            @NotNull Integer minReplicas,
            @NotNull Integer maxReplicas,
            @NotNull Integer cpuUtilization,
            @NotNull Integer memoryUtilization,
            @NotNull UUID applicationId
    ) {
    }

    public record RevisionView(
            long revision,
            String image,
            boolean current
    ) {
    }

    public record ReleaseView(
            String name,
            String namespace,
            String strategy,
            String activeTrack,
            Integer canaryWeight,
            String status,
            String message,
            boolean trafficApplied
    ) {
    }

    public record MetricsServerStatus(boolean available, String message) {
    }
}
