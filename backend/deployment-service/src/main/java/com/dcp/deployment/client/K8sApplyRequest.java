package com.dcp.deployment.client;

import java.util.List;
import java.util.UUID;

public record K8sApplyRequest(
        String name,
        String image,
        int replicas,
        int containerPort,
        UUID actorId,
        UUID applicationId,
        List<RemoteEnvVar> env
) {
}
