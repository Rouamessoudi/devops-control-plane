package com.dcp.kubernetes;

import com.dcp.common.error.ApiException;
import com.dcp.common.policy.ResourcePolicies;
import com.dcp.common.security.JwtPrincipal;
import com.dcp.common.web.CurrentUser;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentCondition;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRoute;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.ContainerMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetrics;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.KubernetesClientTimeoutException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Service
public class KubernetesClusterService {

    private static final String MANAGED_LABEL = "app.kubernetes.io/managed-by";
    private static final String MANAGED_BY = "devops-control-plane";
    private static final String APPLICATION_LABEL = "dcp.application-id";

    private final KubernetesClient client;
    private final KubernetesProperties properties;
    private final OperationLog operations;

    public KubernetesClusterService(KubernetesClient client, KubernetesProperties properties, OperationLog operations) {
        this.client = client;
        this.properties = properties;
        this.operations = operations;
    }

    public KubernetesApi.ClusterStatus status() {
        String namespace = properties.getNamespace();
        try {
            ResourcePolicies.dnsLabel(namespace, "namespace");
            return within(() -> {
                client.getKubernetesVersion();
                return new KubernetesApi.ClusterStatus(true, namespace, "Kubernetes API is reachable");
            }, new KubernetesApi.ClusterStatus(false, namespace, "Kubernetes API timed out"));
        } catch (RuntimeException ex) {
            return new KubernetesApi.ClusterStatus(false, namespace, clip(text(ex), 500));
        }
    }

    public List<KubernetesApi.PodView> pods() {
        List<Pod> items = read(() -> client.pods().inNamespace(namespace()).list().getItems());
        return mapPods(items, podMetrics());
    }

    public KubernetesApi.PodView pod(String name) {
        String safe = ResourcePolicies.dnsLabel(name, "name");
        Pod pod = read(() -> client.pods().inNamespace(namespace()).withName(safe).get());
        if (pod == null) {
            throw ApiException.notFound("Pod not found");
        }
        return toPod(pod, podMetrics().get(safe));
    }

    public List<KubernetesApi.NamespaceView> namespaces() {
        List<Namespace> items = read(() -> client.namespaces().list().getItems());
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<KubernetesApi.NamespaceView> views = new ArrayList<>();
        for (Namespace item : items) {
            String name = item.getMetadata() == null ? null : item.getMetadata().getName();
            String status = item.getStatus() == null ? null : item.getStatus().getPhase();
            if (name != null) {
                views.add(new KubernetesApi.NamespaceView(name, status));
            }
        }
        return views;
    }

    public String podLogs(String name, int tail) {
        String safe = ResourcePolicies.dnsLabel(name, "name");
        if (tail < 1) {
            throw ApiException.badRequest("tail must be at least 1");
        }
        int lines = Math.min(tail, 1000);
        String log = read(() -> client.pods().inNamespace(namespace()).withName(safe).tailingLines(lines).getLog());
        return log == null ? "" : log;
    }

    public List<KubernetesApi.ServiceView> services() {
        List<io.fabric8.kubernetes.api.model.Service> items = read(() -> client.services().inNamespace(namespace()).list().getItems());
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<KubernetesApi.ServiceView> views = new ArrayList<>();
        for (io.fabric8.kubernetes.api.model.Service service : items) {
            views.add(toService(service));
        }
        return views;
    }

    public List<KubernetesApi.IngressView> ingresses() {
        List<Ingress> items = read(() -> client.network().v1().ingresses().inNamespace(namespace()).list().getItems());
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<KubernetesApi.IngressView> views = new ArrayList<>();
        for (Ingress ingress : items) {
            views.add(toIngress(ingress));
        }
        return views;
    }

    public List<KubernetesApi.DeploymentView> deployments() {
        List<Deployment> items = read(() -> client.apps().deployments().inNamespace(namespace()).list().getItems());
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<KubernetesApi.DeploymentView> views = new ArrayList<>();
        for (Deployment deployment : items) {
            views.add(toDeployment(deployment));
        }
        return views;
    }

    public List<KubernetesApi.EventView> events() {
        List<Event> items = read(() -> client.resources(Event.class).inNamespace(namespace()).list().getItems());
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<KubernetesApi.EventView> views = new ArrayList<>();
        for (Event event : items) {
            views.add(toEvent(event));
        }
        return views;
    }

    public KubernetesApi.K8sDeploymentView deploy(KubernetesApi.DeployRequest request) {
        String name = ResourcePolicies.dnsLabel(request.name(), "name");
        ResourcePolicies.replicas(request.replicas(), properties.getMaxReplicas());
        if (request.containerPort() < 1 || request.containerPort() > 65535) {
            throw ApiException.badRequest("containerPort is not valid");
        }
        ImagePolicy.require(request.image(), properties.getAllowedRegistries());
        List<KubernetesApi.EnvBinding> env = request.env() == null ? List.of() : request.env();
        Map<String, String> secretData = new LinkedHashMap<>();
        List<EnvVar> envVars = envVars(env, name, secretData);
        String namespace = namespace();
        try {
            rejectUnmanaged(namespace, name, !secretData.isEmpty());
            applySecret(namespace, name, request.applicationId(), secretData);
            applyDeployment(namespace, name, request, envVars);
            applyService(namespace, name, request.applicationId(), request.containerPort());
            String warning = applyIngress(namespace, name, request.applicationId());
            Deployment current = awaitReady(namespace, name, request.replicas());
            KubernetesApi.K8sDeploymentView view = toResult(current, request.image(), warning);
            operations.record(request.actorId(), "DEPLOY", name, view.status(), view.message());
            return view;
        } catch (RuntimeException ex) {
            RuntimeException mapped = translate(ex);
            operations.record(request.actorId(), "DEPLOY", name, "FAILED", mapped.getMessage());
            throw mapped;
        }
    }

    public KubernetesApi.K8sDeploymentView scale(String name, int replicas) {
        JwtPrincipal user = CurrentUser.require();
        ResourcePolicies.replicas(replicas, properties.getMaxReplicas());
        Deployment existing = requireManaged(name);
        String safe = existing.getMetadata().getName();
        try {
            client.apps().deployments().inNamespace(namespace()).withName(safe).scale(replicas);
            Deployment updated = client.apps().deployments().inNamespace(namespace()).withName(safe).get();
            if (updated == null) {
                throw ApiException.unavailable("Deployment was not readable");
            }
            KubernetesApi.K8sDeploymentView view = toResult(updated, null, null);
            operations.record(user.userId(), "SCALE", safe, view.status(), "Scaled to " + replicas);
            return view;
        } catch (RuntimeException ex) {
            RuntimeException mapped = translate(ex);
            operations.record(user.userId(), "SCALE", safe, "FAILED", mapped.getMessage());
            throw mapped;
        }
    }

    public KubernetesApi.K8sDeploymentView restart(String name) {
        return mutate(name, "RESTART", "Restarted", () -> {
            String safe = ResourcePolicies.dnsLabel(name, "name");
            client.apps().deployments().inNamespace(namespace()).withName(safe).rolling().restart();
        });
    }

    public List<KubernetesApi.RevisionView> revisions(String name) {
        requireAdmin();
        Deployment deployment = requireManaged(name);
        String safe = deployment.getMetadata().getName();
        long current = currentRevision(deployment);
        List<KubernetesApi.RevisionView> views = new ArrayList<>();
        List<ReplicaSet> sets = read(() -> client.apps().replicaSets().inNamespace(namespace()).list().getItems());
        if (sets == null) {
            return List.of();
        }
        for (ReplicaSet set : sets) {
            if (!ownedBy(set, safe)) {
                continue;
            }
            long revision = revisionOf(set);
            if (revision < 1) {
                continue;
            }
            views.add(new KubernetesApi.RevisionView(revision, containerImage(set), revision == current));
        }
        views.sort((left, right) -> Long.compare(right.revision(), left.revision()));
        return views;
    }

    public KubernetesApi.K8sDeploymentView rollback(String name, Integer revision) {
        requireAdmin();
        String detail = revision == null ? "Rolled back to the previous revision" : "Rolled back to revision " + revision;
        return mutate(name, "ROLLBACK", detail, () -> {
            String safe = ResourcePolicies.dnsLabel(name, "name");
            if (revision == null) {
                client.apps().deployments().inNamespace(namespace()).withName(safe).rolling().undo();
            } else {
                restoreRevision(safe, revision);
            }
            Deployment existing = client.apps().deployments().inNamespace(namespace()).withName(safe).get();
            int desired = existing == null || existing.getSpec() == null || existing.getSpec().getReplicas() == null
                    ? 1
                    : existing.getSpec().getReplicas();
            awaitReady(namespace(), safe, desired);
        });
    }

    public KubernetesApi.MetricsServerStatus metricsServer() {
        try {
            return within(() -> {
                boolean available = client.supports(PodMetrics.class);
                String message = available
                        ? "metrics.k8s.io is available"
                        : "metrics.k8s.io is not installed; an HPA will not change replica counts";
                return new KubernetesApi.MetricsServerStatus(available, message);
            }, new KubernetesApi.MetricsServerStatus(false, "Kubernetes API timed out"));
        } catch (RuntimeException ex) {
            RuntimeException mapped = translate(ex);
            return new KubernetesApi.MetricsServerStatus(false, mapped.getMessage());
        }
    }

    public KubernetesApi.ReleaseView blueGreen(KubernetesApi.BlueGreenRequest request) {
        JwtPrincipal user = CurrentUser.require();
        String base = ResourcePolicies.dnsLabel(request.name(), "name");
        String track = ReleasePolicies.track(request.candidateTrack());
        String deploymentName = trackedName(base, track);
        validateRelease(request.activeImage(), request.candidateImage(), request.replicas(), request.containerPort());
        String namespace = namespace();
        try {
            Deployment manifest = ReleaseManifests.trackedDeployment(namespace, deploymentName, track, request.candidateImage(), request.replicas(), request.containerPort(), request.applicationId());
            client.apps().deployments().inNamespace(namespace).resource(manifest).createOrReplace();
            Deployment ready = awaitReady(namespace, deploymentName, request.replicas());
            String cause = ReleasePolicies.rollbackCause(request.replicas(), readyCount(ready), 0, failed(ready), readyCount(ready) < request.replicas(), properties.getRollbackMaxRestarts());
            if (cause != null) {
                operations.record(user.userId(), "BLUE_GREEN", base, "FAILED", cause);
                return new KubernetesApi.ReleaseView(base, namespace, "BLUE_GREEN", null, null, "FAILED", cause, false);
            }
            io.fabric8.kubernetes.api.model.Service service = ReleaseManifests.trackService(namespace, base, deploymentName, track, request.containerPort(), request.applicationId());
            client.services().inNamespace(namespace).resource(service).createOrReplace();
            String message = "Service selector points at track " + track + "; previous track was left in place";
            operations.record(user.userId(), "BLUE_GREEN", base, "RUNNING", message);
            return new KubernetesApi.ReleaseView(base, namespace, "BLUE_GREEN", track, null, "RUNNING", message, true);
        } catch (RuntimeException ex) {
            RuntimeException mapped = translate(ex);
            operations.record(user.userId(), "BLUE_GREEN", base, "FAILED", mapped.getMessage());
            throw mapped;
        }
    }

    public KubernetesApi.ReleaseView switchTrack(String name, String track) {
        JwtPrincipal user = CurrentUser.require();
        String base = ResourcePolicies.dnsLabel(name, "name");
        String selected = ReleasePolicies.track(track);
        String deploymentName = trackedName(base, selected);
        String namespace = namespace();
        try {
            Deployment deployment = requireManaged(deploymentName);
            int desired = deployment.getSpec() == null || deployment.getSpec().getReplicas() == null ? 1 : deployment.getSpec().getReplicas();
            String cause = ReleasePolicies.rollbackCause(desired, readyCount(deployment), 0, failed(deployment), readyCount(deployment) < desired, properties.getRollbackMaxRestarts());
            if (cause != null) {
                operations.record(user.userId(), "BLUE_GREEN_SWITCH", base, "FAILED", cause);
                return new KubernetesApi.ReleaseView(base, namespace, "BLUE_GREEN", null, null, "FAILED", cause, false);
            }
            int port = containerPort(deployment);
            io.fabric8.kubernetes.api.model.Service service = ReleaseManifests.trackService(namespace, base, deploymentName, selected, port, applicationId(deployment));
            client.services().inNamespace(namespace).resource(service).createOrReplace();
            String message = "Service selector moved back to track " + selected;
            operations.record(user.userId(), "BLUE_GREEN_SWITCH", base, "RUNNING", message);
            return new KubernetesApi.ReleaseView(base, namespace, "BLUE_GREEN", selected, null, "RUNNING", message, true);
        } catch (RuntimeException ex) {
            RuntimeException mapped = translate(ex);
            operations.record(user.userId(), "BLUE_GREEN_SWITCH", base, "FAILED", mapped.getMessage());
            throw mapped;
        }
    }

    public KubernetesApi.ReleaseView canary(KubernetesApi.CanaryRequest request) {
        JwtPrincipal user = CurrentUser.require();
        String base = ResourcePolicies.dnsLabel(request.name(), "name");
        int weight = ReleasePolicies.canaryWeight(request.canaryWeight());
        validateRelease(request.stableImage(), request.canaryImage(), request.replicas(), request.containerPort());
        String namespace = namespace();
        String stableName = trackedName(base, "stable");
        String canaryName = trackedName(base, "canary");
        try {
            client.apps().deployments().inNamespace(namespace).resource(ReleaseManifests.trackedDeployment(
                    namespace, stableName, "stable", request.stableImage(), request.replicas(), request.containerPort(), request.applicationId())).createOrReplace();
            client.apps().deployments().inNamespace(namespace).resource(ReleaseManifests.trackedDeployment(
                    namespace, canaryName, "canary", request.canaryImage(), request.replicas(), request.containerPort(), request.applicationId())).createOrReplace();
            client.services().inNamespace(namespace).resource(ReleaseManifests.trackService(
                    namespace, stableName, stableName, "stable", request.containerPort(), request.applicationId())).createOrReplace();
            client.services().inNamespace(namespace).resource(ReleaseManifests.trackService(
                    namespace, canaryName, canaryName, "canary", request.containerPort(), request.applicationId())).createOrReplace();
            Deployment stableReady = awaitReady(namespace, stableName, request.replicas());
            Deployment canaryReady = awaitReady(namespace, canaryName, request.replicas());
            if (readyCount(stableReady) < request.replicas() || readyCount(canaryReady) < request.replicas()) {
                String message = "Pods are not Ready. HTTPRoute weights were not applied.";
                operations.record(user.userId(), "CANARY", base, "FAILED", message);
                return new KubernetesApi.ReleaseView(base, namespace, "CANARY", "canary", weight, "FAILED", message, false);
            }
            String gateway = ResourcePolicies.dnsLabel(properties.getGatewayName(), "gateway");
            client.resource(ReleaseManifests.canaryRoute(namespace, base, gateway, stableName, canaryName, weight)).createOrReplace();
            HTTPRoute programmed = awaitRoute(namespace, base);
            if (!GatewayRouteStatus.accepted(programmed)) {
                String message = "HTTPRoute was stored but the Gateway has not accepted it. Traffic split is not confirmed.";
                operations.record(user.userId(), "CANARY", base, "FAILED", message);
                return new KubernetesApi.ReleaseView(base, namespace, "CANARY", "canary", weight, "FAILED", message, false);
            }
            String host = ReleasePolicies.routeHost(base);
            String message = "HTTPRoute " + base + " accepted by Gateway " + gateway
                    + " for host " + host
                    + ". stable=" + (100 - weight) + " canary=" + weight
                    + ". Acceptance is not a counted HTTP split.";
            operations.record(user.userId(), "CANARY", base, "APPLIED", message);
            return new KubernetesApi.ReleaseView(base, namespace, "CANARY", "canary", weight, "APPLIED", message, true);
        } catch (RuntimeException ex) {
            RuntimeException mapped = translate(ex);
            String message = mapped.getMessage() == null ? "Canary route was not applied" : mapped.getMessage();
            if (message.toLowerCase().contains("httproute") || message.toLowerCase().contains("not found") || message.toLowerCase().contains("the server could not find")) {
                message = "Gateway API HTTPRoute was not applied. Weighted traffic is unchanged. " + message;
            }
            operations.record(user.userId(), "CANARY", base, "FAILED", message);
            throw ApiException.unavailable(message);
        }
    }

    public KubernetesApi.ReleaseView autoscaler(String name, KubernetesApi.HpaRequest request) {
        JwtPrincipal user = CurrentUser.require();
        String deploymentName = ResourcePolicies.dnsLabel(name, "name");
        ReleasePolicies.autoscaler(request.minReplicas(), request.maxReplicas(), request.cpuUtilization(), request.memoryUtilization(), properties.getMaxReplicas());
        requireManaged(deploymentName);
        String namespace = namespace();
        boolean metrics = metricsServer().available();
        try {
            client.autoscaling().v2().horizontalPodAutoscalers().inNamespace(namespace).resource(ReleaseManifests.autoscaler(
                    namespace, deploymentName, deploymentName, request.minReplicas(), request.maxReplicas(),
                    request.cpuUtilization(), request.memoryUtilization(), request.applicationId())).createOrReplace();
            String message = metrics
                    ? "HorizontalPodAutoscaler applied"
                    : "HorizontalPodAutoscaler applied, but metrics.k8s.io is absent so replica counts will not change";
            operations.record(user.userId(), "HPA", deploymentName, metrics ? "APPLIED" : "WAITING_METRICS", message);
            return new KubernetesApi.ReleaseView(deploymentName, namespace, "HPA", null, null, metrics ? "APPLIED" : "WAITING_METRICS", message, metrics);
        } catch (RuntimeException ex) {
            RuntimeException mapped = translate(ex);
            operations.record(user.userId(), "HPA", deploymentName, "FAILED", mapped.getMessage());
            throw mapped;
        }
    }

    private void validateRelease(String firstImage, String secondImage, int replicas, int containerPort) {
        ResourcePolicies.replicas(replicas, properties.getMaxReplicas());
        if (containerPort < 1 || containerPort > 65535) {
            throw ApiException.badRequest("containerPort is not valid");
        }
        ImagePolicy.require(firstImage, properties.getAllowedRegistries());
        ImagePolicy.require(secondImage, properties.getAllowedRegistries());
    }

    private static String trackedName(String base, String track) {
        return ResourcePolicies.dnsLabel(base + "-" + track, "name");
    }

    private static int containerPort(Deployment deployment) {
        if (deployment.getSpec() == null || deployment.getSpec().getTemplate() == null
                || deployment.getSpec().getTemplate().getSpec() == null
                || deployment.getSpec().getTemplate().getSpec().getContainers() == null
                || deployment.getSpec().getTemplate().getSpec().getContainers().isEmpty()
                || deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getPorts() == null
                || deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getPorts().isEmpty()
                || deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getPorts().get(0).getContainerPort() == null) {
            return 8080;
        }
        return deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getPorts().get(0).getContainerPort();
    }

    private static UUID applicationId(Deployment deployment) {
        if (deployment.getMetadata() == null || deployment.getMetadata().getLabels() == null) {
            throw ApiException.badRequest("Deployment has no application id");
        }
        String value = deployment.getMetadata().getLabels().get(APPLICATION_LABEL);
        if (value == null) {
            throw ApiException.badRequest("Deployment has no application id");
        }
        return UUID.fromString(value);
    }

    private KubernetesApi.K8sDeploymentView mutate(String name, String operation, String message, Runnable action) {
        JwtPrincipal user = CurrentUser.require();
        Deployment existing = requireManaged(name);
        String safe = existing.getMetadata().getName();
        try {
            action.run();
            Deployment updated = client.apps().deployments().inNamespace(namespace()).withName(safe).get();
            if (updated == null) {
                throw ApiException.unavailable("Deployment was not readable");
            }
            KubernetesApi.K8sDeploymentView view = toResult(updated, null, null);
            operations.record(user.userId(), operation, safe, view.status(), message);
            return view;
        } catch (RuntimeException ex) {
            RuntimeException mapped = translate(ex);
            operations.record(user.userId(), operation, safe, "FAILED", mapped.getMessage());
            throw mapped;
        }
    }

    private void restoreRevision(String name, int revision) {
        String namespace = namespace();
        Deployment deployment = client.apps().deployments().inNamespace(namespace).withName(name).get();
        if (deployment == null || deployment.getSpec() == null) {
            throw ApiException.notFound("Deployment was not found");
        }
        if (revision == currentRevision(deployment)) {
            throw ApiException.conflict("Deployment is already at revision " + revision);
        }
        ReplicaSet match = null;
        List<ReplicaSet> sets = client.apps().replicaSets().inNamespace(namespace).list().getItems();
        if (sets != null) {
            for (ReplicaSet set : sets) {
                if (ownedBy(set, name) && revisionOf(set) == revision) {
                    match = set;
                }
            }
        }
        if (match == null || match.getSpec() == null || match.getSpec().getTemplate() == null) {
            throw ApiException.badRequest("Revision " + revision + " was not found");
        }
        var template = match.getSpec().getTemplate();
        if (template.getMetadata() != null && template.getMetadata().getLabels() != null) {
            Map<String, String> labels = new LinkedHashMap<>(template.getMetadata().getLabels());
            labels.remove("pod-template-hash");
            template.getMetadata().setLabels(labels);
        }
        deployment.getSpec().setTemplate(template);
        client.apps().deployments().inNamespace(namespace).withName(name).replace(deployment);
    }

    private HTTPRoute awaitRoute(String namespace, String name) {
        try {
            HTTPRoute route = client.resources(HTTPRoute.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .waitUntilCondition(GatewayRouteStatus::accepted, 60, TimeUnit.SECONDS);
            if (route != null) {
                return route;
            }
        } catch (KubernetesClientTimeoutException ex) {
            // The caller reports that the Gateway did not accept the route.
        }
        return client.resources(HTTPRoute.class).inNamespace(namespace).withName(name).get();
    }

    private static void requireAdmin() {
        JwtPrincipal user = CurrentUser.require();
        if (!user.hasRole("ADMIN")) {
            throw ApiException.forbidden("Administrator role is required for Kubernetes rollback");
        }
    }

    private static boolean ownedBy(ReplicaSet set, String deploymentName) {
        if (set.getMetadata() == null || set.getMetadata().getOwnerReferences() == null) {
            return false;
        }
        for (OwnerReference reference : set.getMetadata().getOwnerReferences()) {
            if ("Deployment".equals(reference.getKind()) && deploymentName.equals(reference.getName())) {
                return true;
            }
        }
        return false;
    }

    private static long revisionOf(ReplicaSet set) {
        return annotationRevision(set.getMetadata());
    }

    private static long currentRevision(Deployment deployment) {
        return annotationRevision(deployment.getMetadata());
    }

    private static long annotationRevision(ObjectMeta metadata) {
        if (metadata == null || metadata.getAnnotations() == null) {
            return -1;
        }
        String value = metadata.getAnnotations().get("deployment.kubernetes.io/revision");
        if (value == null || value.isBlank()) {
            return -1;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static String containerImage(ReplicaSet set) {
        if (set.getSpec() == null || set.getSpec().getTemplate() == null
                || set.getSpec().getTemplate().getSpec() == null
                || set.getSpec().getTemplate().getSpec().getContainers() == null
                || set.getSpec().getTemplate().getSpec().getContainers().isEmpty()
                || set.getSpec().getTemplate().getSpec().getContainers().get(0).getImage() == null) {
            return "";
        }
        return set.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
    }

    private void rejectUnmanaged(String namespace, String name, boolean replacingSecret) {
        Deployment deployment = client.apps().deployments().inNamespace(namespace).withName(name).get();
        if (deployment != null && !managed(deployment.getMetadata())) {
            throw ApiException.conflict("Deployment name is already used");
        }
        io.fabric8.kubernetes.api.model.Service service = client.services().inNamespace(namespace).withName(name).get();
        if (service != null && !managed(service.getMetadata())) {
            throw ApiException.conflict("Service name is already used");
        }
        if (replacingSecret) {
            Secret secret = client.secrets().inNamespace(namespace).withName(name).get();
            if (secret != null && !managed(secret.getMetadata())) {
                throw ApiException.conflict("Secret name is already used");
            }
        }
    }

    private void applySecret(String namespace, String name, UUID applicationId, Map<String, String> secretData) {
        if (secretData.isEmpty()) {
            Secret existing = client.secrets().inNamespace(namespace).withName(name).get();
            if (existing != null && managed(existing.getMetadata())) {
                client.secrets().inNamespace(namespace).withName(name).delete();
            }
            return;
        }
        Secret secret = new SecretBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .addToLabels(MANAGED_LABEL, MANAGED_BY)
                    .addToLabels(APPLICATION_LABEL, applicationId.toString())
                .endMetadata()
                .withStringData(secretData)
                .build();
        client.secrets().inNamespace(namespace).resource(secret).createOrReplace();
    }

    private void applyDeployment(String namespace, String name, KubernetesApi.DeployRequest request, List<EnvVar> envVars) {
        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .addToLabels("app", name)
                    .addToLabels(MANAGED_LABEL, MANAGED_BY)
                    .addToLabels(APPLICATION_LABEL, request.applicationId().toString())
                .endMetadata()
                .withNewSpec()
                    .withReplicas(request.replicas())
                    .withNewSelector()
                        .addToMatchLabels("app", name)
                    .endSelector()
                    .withNewTemplate()
                        .withNewMetadata()
                            .addToLabels("app", name)
                            .addToLabels(MANAGED_LABEL, MANAGED_BY)
                            .addToLabels(APPLICATION_LABEL, request.applicationId().toString())
                        .endMetadata()
                        .withNewSpec()
                            .addNewContainer()
                                .withName(name)
                                .withImage(request.image())
                                .withImagePullPolicy("IfNotPresent")
                                .addNewPort()
                                    .withContainerPort(request.containerPort())
                                .endPort()
                                .withEnv(envVars)
                                .withNewReadinessProbe()
                                    .withNewTcpSocket()
                                        .withPort(new IntOrString(request.containerPort()))
                                    .endTcpSocket()
                                .endReadinessProbe()
                                .withNewLivenessProbe()
                                    .withNewTcpSocket()
                                        .withPort(new IntOrString(request.containerPort()))
                                    .endTcpSocket()
                                .endLivenessProbe()
                                .withNewResources()
                                    .addToRequests("cpu", new Quantity("50m"))
                                    .addToRequests("memory", new Quantity("64Mi"))
                                    .addToLimits("cpu", new Quantity("500m"))
                                    .addToLimits("memory", new Quantity("256Mi"))
                                .endResources()
                            .endContainer()
                        .endSpec()
                    .endTemplate()
                .endSpec()
                .build();
        client.apps().deployments().inNamespace(namespace).resource(deployment).createOrReplace();
    }

    private void applyService(String namespace, String name, UUID applicationId, int containerPort) {
        io.fabric8.kubernetes.api.model.Service service = new io.fabric8.kubernetes.api.model.ServiceBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .addToLabels(MANAGED_LABEL, MANAGED_BY)
                    .addToLabels(APPLICATION_LABEL, applicationId.toString())
                .endMetadata()
                .withNewSpec()
                    .addToSelector("app", name)
                    .addNewPort()
                        .withName("http")
                        .withPort(80)
                        .withTargetPort(new IntOrString(containerPort))
                        .withProtocol("TCP")
                    .endPort()
                .endSpec()
                .build();
        client.services().inNamespace(namespace).resource(service).createOrReplace();
    }

    private String applyIngress(String namespace, String name, UUID applicationId) {
        try {
            Ingress ingress = new IngressBuilder()
                    .withNewMetadata()
                        .withName(name)
                        .withNamespace(namespace)
                        .addToLabels(MANAGED_LABEL, MANAGED_BY)
                        .addToLabels(APPLICATION_LABEL, applicationId.toString())
                    .endMetadata()
                    .withNewSpec()
                        .addNewRule()
                            .withHost(name + ".dcp.local")
                            .withNewHttp()
                                .addNewPath()
                                    .withPath("/")
                                    .withPathType("Prefix")
                                    .withNewBackend()
                                        .withNewService()
                                            .withName(name)
                                            .withNewPort()
                                                .withNumber(80)
                                            .endPort()
                                        .endService()
                                    .endBackend()
                                .endPath()
                            .endHttp()
                        .endRule()
                    .endSpec()
                    .build();
            client.network().v1().ingresses().inNamespace(namespace).resource(ingress).createOrReplace();
            return null;
        } catch (Exception ex) {
            return "Ingress was not created: " + clip(text(ex), 400);
        }
    }

    private Deployment awaitReady(String namespace, String name, int replicas) {
        try {
            Deployment ready = client.apps().deployments()
                    .inNamespace(namespace)
                    .withName(name)
                    .waitUntilCondition(current -> readyCount(current) >= replicas, 90, TimeUnit.SECONDS);
            if (ready != null) {
                return ready;
            }
        } catch (KubernetesClientTimeoutException ex) {
            // The caller receives PENDING when no replica becomes ready in time.
        }
        Deployment current = client.apps().deployments().inNamespace(namespace).withName(name).get();
        if (current == null) {
            throw ApiException.unavailable("Deployment was not readable after creation");
        }
        return current;
    }

    private Deployment requireManaged(String name) {
        String safe = ResourcePolicies.dnsLabel(name, "name");
        Deployment deployment = read(() -> client.apps().deployments().inNamespace(namespace()).withName(safe).get());
        if (deployment == null || !managed(deployment.getMetadata())) {
            throw ApiException.notFound("Deployment not found");
        }
        return deployment;
    }

    private String namespace() {
        return ResourcePolicies.dnsLabel(properties.getNamespace(), "namespace");
    }

    private static <T> T within(Supplier<T> call, T onTimeout) {
        CompletableFuture<T> task = CompletableFuture.supplyAsync(call);
        try {
            return task.get(4, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            task.cancel(true);
            return onTimeout;
        } catch (ExecutionException ex) {
            task.cancel(true);
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(cause);
        } catch (InterruptedException ex) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            throw ApiException.unavailable("Kubernetes API is unavailable");
        }
    }

    private <T> T read(Supplier<T> call) {
        try {
            return call.get();
        } catch (RuntimeException ex) {
            throw translate(ex);
        }
    }

    private RuntimeException translate(RuntimeException ex) {
        if (ex instanceof ApiException) {
            return ex;
        }
        if (ex instanceof KubernetesClientTimeoutException) {
            return ApiException.unavailable("Kubernetes API timed out");
        }
        if (ex instanceof KubernetesClientException clientException) {
            int code = clientException.getCode();
            if (code == 404) {
                return ApiException.notFound("Resource not found");
            }
            if (code == 409) {
                return ApiException.conflict(clip(text(clientException), 500));
            }
            if (code == 400 || code == 422) {
                return ApiException.badRequest(clip(text(clientException), 500));
            }
            if (code == 401 || code == 403) {
                return ApiException.forbidden("Kubernetes API rejected the call");
            }
            if (code <= 0 || connectionFailure(clientException)) {
                return ApiException.unavailable("Kubernetes API is unavailable");
            }
            return ApiException.unavailable(clip(text(clientException), 500));
        }
        if (connectionFailure(ex)) {
            return ApiException.unavailable("Kubernetes API is unavailable");
        }
        return ex;
    }

    private static boolean connectionFailure(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof java.net.ConnectException
                    || current instanceof java.net.UnknownHostException
                    || current instanceof java.net.SocketException) {
                return true;
            }
            String name = current.getClass().getName();
            if (name.contains("ConnectException") || name.contains("UnknownHostException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static List<EnvVar> envVars(List<KubernetesApi.EnvBinding> env, String secretName, Map<String, String> secretData) {
        List<EnvVar> vars = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (KubernetesApi.EnvBinding binding : env) {
            if (binding == null) {
                throw ApiException.badRequest("Environment variable is not allowed");
            }
            String key = ResourcePolicies.envKey(binding.key());
            if (!seen.add(key)) {
                throw ApiException.badRequest("Duplicate environment variable");
            }
            if (binding.secret()) {
                secretData.put(key, binding.value() == null ? "" : binding.value());
                vars.add(new EnvVarBuilder()
                        .withName(key)
                        .withNewValueFrom()
                            .withNewSecretKeyRef()
                                .withName(secretName)
                                .withKey(key)
                            .endSecretKeyRef()
                        .endValueFrom()
                        .build());
            } else {
                vars.add(new EnvVarBuilder().withName(key).withValue(binding.value() == null ? "" : binding.value()).build());
            }
        }
        return vars;
    }

    private static boolean managed(ObjectMeta metadata) {
        return metadata != null && metadata.getLabels() != null && MANAGED_BY.equals(metadata.getLabels().get(MANAGED_LABEL));
    }

    private static int readyCount(Deployment deployment) {
        if (deployment == null || deployment.getStatus() == null || deployment.getStatus().getReadyReplicas() == null) {
            return 0;
        }
        return deployment.getStatus().getReadyReplicas();
    }

    private static boolean failed(Deployment deployment) {
        if (deployment.getStatus() == null || deployment.getStatus().getConditions() == null) {
            return false;
        }
        for (DeploymentCondition condition : deployment.getStatus().getConditions()) {
            if ("ReplicaFailure".equals(condition.getType()) && "True".equals(condition.getStatus())) {
                return true;
            }
            if ("Progressing".equals(condition.getType())
                    && "False".equals(condition.getStatus())
                    && "ProgressDeadlineExceeded".equals(condition.getReason())) {
                return true;
            }
        }
        return false;
    }

    private static String phase(Deployment deployment, int replicas) {
        if (replicas > 0 && readyCount(deployment) >= replicas) {
            return "RUNNING";
        }
        if (failed(deployment)) {
            return "FAILED";
        }
        return "PENDING";
    }

    private static String messageFor(String status, String warning) {
        String base = switch (status) {
            case "RUNNING" -> "Ready";
            case "FAILED" -> "Deployment failed";
            default -> "Waiting for ready replicas";
        };
        if (warning == null || warning.isBlank()) {
            return base;
        }
        return clip(base + ". " + warning, 1000);
    }

    private KubernetesApi.K8sDeploymentView toResult(Deployment deployment, String fallbackImage, String warning) {
        String name = deployment.getMetadata() == null ? null : deployment.getMetadata().getName();
        String namespace = deployment.getMetadata() == null ? null : deployment.getMetadata().getNamespace();
        String image = fallbackImage;
        int replicas = 0;
        if (deployment.getSpec() != null) {
            if (deployment.getSpec().getReplicas() != null) {
                replicas = deployment.getSpec().getReplicas();
            }
            if (deployment.getSpec().getTemplate() != null
                    && deployment.getSpec().getTemplate().getSpec() != null
                    && deployment.getSpec().getTemplate().getSpec().getContainers() != null
                    && !deployment.getSpec().getTemplate().getSpec().getContainers().isEmpty()
                    && deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage() != null) {
                image = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
            }
        }
        String revision = null;
        if (deployment.getMetadata() != null && deployment.getMetadata().getAnnotations() != null) {
            revision = deployment.getMetadata().getAnnotations().get("deployment.kubernetes.io/revision");
        }
        String status = phase(deployment, replicas);
        return new KubernetesApi.K8sDeploymentView(name, namespace, image, replicas, readyCount(deployment), revision, status, messageFor(status, warning));
    }

    private Map<String, PodMetrics> podMetrics() {
        Map<String, PodMetrics> usage = new LinkedHashMap<>();
        try {
            var listed = client.top().pods().inNamespace(namespace()).metrics();
            if (listed == null || listed.getItems() == null) {
                return usage;
            }
            for (PodMetrics item : listed.getItems()) {
                if (item.getMetadata() != null && item.getMetadata().getName() != null) {
                    usage.put(item.getMetadata().getName(), item);
                }
            }
        } catch (RuntimeException ex) {
            return usage;
        }
        return usage;
    }

    private static List<KubernetesApi.PodView> mapPods(List<Pod> items, Map<String, PodMetrics> usage) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<KubernetesApi.PodView> views = new ArrayList<>();
        for (Pod pod : items) {
            String name = pod.getMetadata() == null ? null : pod.getMetadata().getName();
            views.add(toPod(pod, name == null ? null : usage.get(name)));
        }
        return views;
    }

    private static KubernetesApi.PodView toPod(Pod pod, PodMetrics metrics) {
        String name = metaName(pod.getMetadata());
        String namespace = metaNamespace(pod.getMetadata());
        String createdAt = pod.getMetadata() == null ? null : pod.getMetadata().getCreationTimestamp();
        String phase = pod.getStatus() == null ? null : pod.getStatus().getPhase();
        String node = pod.getSpec() == null ? null : pod.getSpec().getNodeName();
        int restarts = 0;
        List<String> images = new ArrayList<>();
        if (pod.getStatus() != null && pod.getStatus().getContainerStatuses() != null) {
            for (ContainerStatus status : pod.getStatus().getContainerStatuses()) {
                if (status.getRestartCount() != null) {
                    restarts += status.getRestartCount();
                }
                if (status.getImage() != null) {
                    images.add(status.getImage());
                }
            }
        }
        if (images.isEmpty() && pod.getSpec() != null && pod.getSpec().getContainers() != null) {
            pod.getSpec().getContainers().forEach(container -> {
                if (container.getImage() != null) {
                    images.add(container.getImage());
                }
            });
        }
        String cpu = null;
        String memory = null;
        if (metrics != null && metrics.getContainers() != null) {
            List<String> cpus = new ArrayList<>();
            List<String> memories = new ArrayList<>();
            for (ContainerMetrics container : metrics.getContainers()) {
                if (container.getUsage() == null) {
                    continue;
                }
                Quantity cpuQuantity = container.getUsage().get("cpu");
                Quantity memoryQuantity = container.getUsage().get("memory");
                if (cpuQuantity != null) {
                    cpus.add(cpuQuantity.toString());
                }
                if (memoryQuantity != null) {
                    memories.add(memoryQuantity.toString());
                }
            }
            cpu = cpus.isEmpty() ? null : String.join(", ", cpus);
            memory = memories.isEmpty() ? null : String.join(", ", memories);
        }
        return new KubernetesApi.PodView(name, namespace, phase, restarts, node, List.copyOf(images), createdAt, cpu, memory);
    }

    private static KubernetesApi.DeploymentView toDeployment(Deployment deployment) {
        Integer replicas = deployment.getSpec() == null ? null : deployment.getSpec().getReplicas();
        Integer ready = deployment.getStatus() == null ? null : deployment.getStatus().getReadyReplicas();
        return new KubernetesApi.DeploymentView(
                metaName(deployment.getMetadata()),
                metaNamespace(deployment.getMetadata()),
                replicas,
                ready,
                containerImages(deployment),
                deployment.getMetadata() == null ? null : deployment.getMetadata().getCreationTimestamp());
    }

    private static List<String> containerImages(Deployment deployment) {
        if (deployment.getSpec() == null || deployment.getSpec().getTemplate() == null
                || deployment.getSpec().getTemplate().getSpec() == null
                || deployment.getSpec().getTemplate().getSpec().getContainers() == null) {
            return List.of();
        }
        List<String> images = new ArrayList<>();
        deployment.getSpec().getTemplate().getSpec().getContainers().forEach(container -> {
            if (container.getImage() != null) {
                images.add(container.getImage());
            }
        });
        return images;
    }

    private static KubernetesApi.ServiceView toService(io.fabric8.kubernetes.api.model.Service service) {
        List<String> ports = new ArrayList<>();
        String type = null;
        String clusterIP = null;
        if (service.getSpec() != null) {
            type = service.getSpec().getType();
            clusterIP = service.getSpec().getClusterIP();
            if (service.getSpec().getPorts() != null) {
                for (ServicePort port : service.getSpec().getPorts()) {
                    String target = port.getTargetPort() == null ? "" : port.getTargetPort().toString();
                    ports.add(port.getPort() + "->" + target);
                }
            }
        }
        return new KubernetesApi.ServiceView(
                metaName(service.getMetadata()),
                metaNamespace(service.getMetadata()),
                type,
                clusterIP,
                ports,
                service.getMetadata() == null ? null : service.getMetadata().getCreationTimestamp());
    }

    private static KubernetesApi.IngressView toIngress(Ingress ingress) {
        List<String> hosts = new ArrayList<>();
        if (ingress.getSpec() != null && ingress.getSpec().getRules() != null) {
            ingress.getSpec().getRules().forEach(rule -> {
                if (rule.getHost() != null) {
                    hosts.add(rule.getHost());
                }
            });
        }
        return new KubernetesApi.IngressView(
                metaName(ingress.getMetadata()),
                metaNamespace(ingress.getMetadata()),
                hosts,
                ingress.getMetadata() == null ? null : ingress.getMetadata().getCreationTimestamp());
    }

    private static KubernetesApi.EventView toEvent(Event event) {
        String involved = null;
        if (event.getInvolvedObject() != null) {
            involved = event.getInvolvedObject().getKind() + "/" + event.getInvolvedObject().getName();
        }
        return new KubernetesApi.EventView(
                metaName(event.getMetadata()),
                metaNamespace(event.getMetadata()),
                event.getReason(),
                event.getMessage(),
                event.getType(),
                involved,
                event.getMetadata() == null ? null : event.getMetadata().getCreationTimestamp());
    }

    private static String metaName(ObjectMeta metadata) {
        return metadata == null ? null : metadata.getName();
    }

    private static String metaNamespace(ObjectMeta metadata) {
        return metadata == null ? null : metadata.getNamespace();
    }

    private static String text(Throwable ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message;
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
