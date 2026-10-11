package com.dcp.deployment.api;

import java.time.Instant;
import java.util.UUID;

public record DeploymentEventView(
        UUID id,
        UUID deploymentId,
        UUID actorId,
        String action,
        String message,
        Instant createdAt
) {
}
