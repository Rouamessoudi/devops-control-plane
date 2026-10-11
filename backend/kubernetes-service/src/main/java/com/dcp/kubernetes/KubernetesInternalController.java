package com.dcp.kubernetes;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/kubernetes")
@PreAuthorize("hasRole('INTERNAL')")
public class KubernetesInternalController {

    private final KubernetesClusterService cluster;

    public KubernetesInternalController(KubernetesClusterService cluster) {
        this.cluster = cluster;
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

    @GetMapping("/deployments")
    public List<KubernetesApi.DeploymentView> deployments() {
        return cluster.deployments();
    }

    @PostMapping("/deployments")
    public KubernetesApi.K8sDeploymentView deploy(@Valid @RequestBody KubernetesApi.DeployRequest request) {
        return cluster.deploy(request);
    }
}
