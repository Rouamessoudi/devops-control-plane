package com.dcp.kubernetes;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/kubernetes")
public class KubernetesController {

    private final KubernetesClusterService cluster;
    private final OperationLog operations;

    public KubernetesController(KubernetesClusterService cluster, OperationLog operations) {
        this.cluster = cluster;
        this.operations = operations;
    }

    @GetMapping("/status")
    public KubernetesApi.ClusterStatus status() {
        return cluster.status();
    }

    @GetMapping("/pods")
    public List<KubernetesApi.PodView> pods() {
        return cluster.pods();
    }

    @GetMapping("/pods/{name}")
    public KubernetesApi.PodView pod(@PathVariable String name) {
        return cluster.pod(name);
    }

    @GetMapping(value = "/pods/{name}/logs", produces = MediaType.TEXT_PLAIN_VALUE)
    public String logs(@PathVariable String name, @RequestParam(defaultValue = "200") int tail) {
        return cluster.podLogs(name, tail);
    }

    @GetMapping("/services")
    public List<KubernetesApi.ServiceView> services() {
        return cluster.services();
    }

    @GetMapping("/ingresses")
    public List<KubernetesApi.IngressView> ingresses() {
        return cluster.ingresses();
    }

    @GetMapping("/deployments")
    public List<KubernetesApi.DeploymentView> deployments() {
        return cluster.deployments();
    }

    @GetMapping("/events")
    public List<KubernetesApi.EventView> events() {
        return cluster.events();
    }

    @PostMapping("/deployments/{name}/scale")
    public KubernetesApi.K8sDeploymentView scale(@PathVariable String name, @Valid @RequestBody KubernetesApi.ScaleRequest request) {
        return cluster.scale(name, request.replicas());
    }

    @PostMapping("/deployments/{name}/restart")
    public KubernetesApi.K8sDeploymentView restart(@PathVariable String name) {
        return cluster.restart(name);
    }

    @GetMapping("/deployments/{name}/revisions")
    public List<KubernetesApi.RevisionView> revisions(@PathVariable String name) {
        return cluster.revisions(name);
    }

    @PostMapping("/deployments/{name}/rollback")
    public KubernetesApi.K8sDeploymentView rollback(@PathVariable String name,
                                                    @RequestParam(required = false) Integer revision) {
        return cluster.rollback(name, revision);
    }

    @GetMapping("/audit-events")
    public List<KubernetesApi.OperationAudit> audit() {
        return operations.audit();
    }

    @GetMapping("/metrics-server")
    public KubernetesApi.MetricsServerStatus metricsServer() {
        return cluster.metricsServer();
    }

    @PostMapping("/releases/blue-green")
    public KubernetesApi.ReleaseView blueGreen(@Valid @RequestBody KubernetesApi.BlueGreenRequest request) {
        return cluster.blueGreen(request);
    }

    @PostMapping("/releases/{name}/switch")
    public KubernetesApi.ReleaseView switchTrack(@PathVariable String name, @RequestParam String track) {
        return cluster.switchTrack(name, track);
    }

    @PostMapping("/releases/canary")
    public KubernetesApi.ReleaseView canary(@Valid @RequestBody KubernetesApi.CanaryRequest request) {
        return cluster.canary(request);
    }

    @PostMapping("/deployments/{name}/autoscaler")
    public KubernetesApi.ReleaseView autoscaler(@PathVariable String name, @Valid @RequestBody KubernetesApi.HpaRequest request) {
        return cluster.autoscaler(name, request);
    }
}
