package com.dcp.deployment.client;

import java.time.Instant;
import java.util.UUID;

public record RemoteApplication(
        UUID id,
        UUID ownerId,
        String name,
        String description,
        String gitRepositoryUrl,
        String branch,
        String environment,
        String dockerfilePath,
        String contextPath,
        int containerPort,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
