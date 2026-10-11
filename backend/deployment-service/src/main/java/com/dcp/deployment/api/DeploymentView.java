package com.dcp.deployment.api;

import com.dcp.deployment.domain.DeploymentStatus;
import com.dcp.deployment.domain.EnvironmentName;
import com.dcp.deployment.domain.TriggerType;

import java.time.Instant;
import java.util.UUID;

public record DeploymentView(
        UUID id,
        UUID applicationId,
        UUID requestedBy,
        String imageRepository,
        String imageTag,
        String fullImage,
        int replicas,
        EnvironmentName environment,
        TriggerType trigger,
        DeploymentStatus status,
        String k8sDeploymentName,
        Long k8sRevision,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
) {
}
