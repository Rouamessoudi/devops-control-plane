package com.dcp.deployment.client;

public record K8sDeploymentView(
        String name,
        String namespace,
        String image,
        int replicas,
        int readyReplicas,
        Long revision,
        String status
) {
}
